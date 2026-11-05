package com.greenthumb.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenthumb.dto.AiDiagnosisRequest;
import com.greenthumb.entity.DiseaseInfoEntity;
import com.greenthumb.entity.PlantPhotoEntity;
import com.greenthumb.entity.PlantSpeciesEntity;
import com.greenthumb.entity.UserPlantEntity;
import com.greenthumb.mapper.DiseaseInfoMapper;
import com.greenthumb.mapper.PlantSpeciesMapper;
import com.greenthumb.mapper.UserPlantMapper;
import com.greenthumb.service.llm.LlmClient;
import com.greenthumb.vo.AiDiagnosisVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 辅助诊断：<b>自训 CNN 负责识别，大模型只负责解释和建议</b>。
 *
 * <h3>为什么不让大模型参与识别——有实测数据</h3>
 * 在 25 张严格留出的芦荟测试图上：
 * <pre>
 *   aloe6 专用 CNN Top-1 .......... 68%
 *   aloe6 Top-3 命中率 ............ 96%   ← 正确答案 96% 的时候都摆在候选里
 *   大模型从这 3 个候选里复核 ...... 24%   ← 比三选一瞎猜（33%）还差
 * </pre>
 * 结论：把大模型放进识别环节，准确率会从 68% 掉到 24%。
 * 它擅长的是"把已经确定的结论讲清楚、给出处置方案"，不是"看图鉴别病害"。
 *
 * <h3>因此的四条链路</h3>
 * <ol>
 *   <li><b>MODEL</b>：物种覆盖 + 置信度 ≥ 0.6 → CNN 命名 + 知识库建议；
 *       大模型只在此基础上追加一段"通俗解释"（失败了也不影响结论）</li>
 *   <li><b>MODEL_TOP3</b>：物种覆盖但置信度不足 → 给出 Top-3 让用户确认，
 *       大模型只解释"这几种怎么区分"</li>
 *   <li><b>VLM_CATEGORY</b>：物种未被模型覆盖 → 大模型只判断病害<b>大类</b>
 *       并描述症状，<b>明确拒绝给具体病名</b>，附通用处置建议</li>
 *   <li><b>UNAVAILABLE</b>：CNN 不可用且大模型未配置 → 如实说明，绝不编造</li>
 * </ol>
 */
@Service
public class AiDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(AiDiagnosisService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** CNN 置信度门槛 */
    private static final double CNN_TRUST = 0.60;
    private static final int TOP_K = 3;

    /** 解释类提示词：只允许在"已确定的结论"上做文章，不允许改结论 */
    private static final String EXPLAIN_SYSTEM = """
            你是「植语助手」的植物养护顾问。用户已经拿到一个确定的诊断结论，你的任务是把它讲清楚。

            严格遵守：
            1. **不要质疑、不要修改给定的诊断结论**。你的职责是解释和执行建议。
            2. 只依据给定的知识库内容和图片中可见的信息作答，不要引入知识库以外的病害名。
            3. **严禁编造农药名称、浓度、用量**。需要用药时只说"按产品标签说明稀释后使用"。
            4. 严禁编造文献、专家、机构。
            5. 只输出 JSON，不要 markdown 代码块。
            """;

    /** 未覆盖物种的提示词：只判大类，不给病名 */
    private static final String CATEGORY_SYSTEM = """
            你是「植语助手」的植物病害初筛助手。

            严格遵守：
            1. 本地模型**没有**收录这种植物的病害，所以你**不能给出具体病害名称**。
            2. 你的任务只有三件：判断病害大类、描述你看到的症状、给通用处置建议。
            3. category 只能是 FUNGAL / BACTERIAL / PEST / PHYSIOLOGICAL / VIRAL / UNKNOWN 之一。
            4. **严禁编造具体病名**（例如"XX 枯萎病"）；diseaseGuess 字段留空字符串。
            5. 严禁编造农药名称、浓度、用量；需要用药时只写"按产品标签说明稀释"。
            6. 只输出 JSON，不要 markdown 代码块。
            """;

    private final AiClientService aiClient;
    private final LlmClient llm;
    private final PhotoService photoService;
    private final DiseaseInfoMapper diseaseInfoMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final UserPlantMapper userPlantMapper;

    public AiDiagnosisService(AiClientService aiClient, LlmClient llm, PhotoService photoService,
                              DiseaseInfoMapper diseaseInfoMapper, PlantSpeciesMapper speciesMapper,
                              UserPlantMapper userPlantMapper) {
        this.aiClient = aiClient;
        this.llm = llm;
        this.photoService = photoService;
        this.diseaseInfoMapper = diseaseInfoMapper;
        this.speciesMapper = speciesMapper;
        this.userPlantMapper = userPlantMapper;
    }

    public AiDiagnosisVO diagnose(Long userId, AiDiagnosisRequest req) {
        if (req == null || req.photoId() == null) {
            throw new com.greenthumb.common.BizException(
                    com.greenthumb.common.ErrorCode.PARAM_INVALID, "缺少 photoId");
        }
        PlantPhotoEntity photo = photoService.getOwned(userId, req.photoId());
        byte[] bytes = photoService.readBytes(photo);

        PlantSpeciesEntity species = resolveSpecies(userId, req, photo);
        String speciesName = species == null ? null : species.getCommonName();
        String prefix = com.greenthumb.common.SpeciesKeys.keyOf(speciesName);
        boolean covered = prefix != null;

        // ---- 识别：永远先跑 CNN，大模型不参与 ----
        // 传物种 key：有专用模型的物种（如芦荟）走专用模型，准确率明显更高
        AiClientService.Prediction pred =
                aiClient.predictDisease(bytes, photoService.filenameOf(photo), TOP_K, prefix);
        List<AiDiagnosisVO.Item> topK = new ArrayList<>();
        if (pred.available() && pred.topK() != null) {
            for (AiClientService.Item it : pred.topK()) {
                topK.add(new AiDiagnosisVO.Item(it.code(), it.nameCn(), it.confidence()));
            }
        }
        if (!pred.available()) {
            return unavailable("识别模型不可用：" + pred.reason(), topK);
        }
        if (topK.isEmpty()) {
            return unavailable("识别模型没有返回结果", topK);
        }

        AiDiagnosisVO.Item best = topK.get(0);
        double conf = best.confidence();

        // ---- 链路 1：物种覆盖 + 高置信 → 确定结论 + 大模型讲解 ----
        if (covered && conf >= CNN_TRUST) {
            DiseaseInfoEntity info = findByCode(best.code());
            String explain = explain(bytes, speciesName, best, info, req.question());
            log.info("AI 诊断：CNN 结论 {} 置信度 {}", best.nameCn(), String.format("%.2f", conf));
            return new AiDiagnosisVO(
                    "MODEL", best.nameCn(),
                    info == null ? null : info.getCategory(),
                    conf, info != null,
                    info == null ? List.of() : List.of(abbrev(info.getSymptoms(), 240)),
                    info == null ? List.of() : splitLines(info.getCauses()),
                    explain != null ? splitLines(explain) : (info == null ? List.of() : splitLines(info.getTreatment())),
                    info == null ? List.of() : splitLines(info.getPrevention()),
                    false, null, DISCLAIMER,
                    explain != null ? "自训模型识别 + 大模型讲解" : "自训模型识别（大模型未配置，展示知识库原文）",
                    topK);
        }

        // ---- 链路 2：物种覆盖但置信度不足 → 给出最可能的答案 + 标注待确认 ----
        if (covered) {
            // 查一下知识库：即使置信度不足，该病害的处置建议也应该给出来。
            // 早一版这里写死 inKnowledgeBase=false 且 treatment 为空数组，
            // 导致「模型认识这个病、库里也有处置方案，但用户拿到的是一片空白」。
            DiseaseInfoEntity info = findByCode(best.code());
            String ask = candidateExplain(bytes, speciesName, topK, req.question());
            return new AiDiagnosisVO(
                    // 病名照给，但明确标"待确认"——比只丢一句"不确定"有用得多
                    "MODEL_TOP3", best.nameCn() + "（待确认）",
                    info == null ? null : info.getCategory(),
                    conf,
                    info != null,
                    info == null ? List.of() : splitLines(info.getSymptoms()),
                    info == null ? List.of() : splitLines(info.getCauses()),
                    ask != null ? splitLines(ask)
                            : (info == null ? List.of() : splitLines(info.getTreatment())),
                    info == null ? List.of() : splitLines(info.getPrevention()),
                    true,
                    "模型倾向于「" + best.nameCn() + "」，但把握不足。请对照下面的鉴别要点确认；"
                            + "也可以补充描述（斑点颜色、位置、是否凹陷、叶背有没有粉状物）。",
                    DISCLAIMER,
                    String.format("自训模型置信度仅 %.0f%%，低于 %.0f%% 的门槛，因此标注为待确认",
                            conf * 100, CNN_TRUST * 100),
                    topK);
        }

        // ---- 链路 3：物种未被模型覆盖 → 大模型只判大类，拒绝给病名 ----
        return categoryOnly(bytes, speciesName, req.question(), topK);
    }

    // ------------------------------------------------------------------
    // 链路 1：解释已确定的结论
    // ------------------------------------------------------------------

    private String explain(byte[] image, String speciesName, AiDiagnosisVO.Item best,
                           DiseaseInfoEntity info, String question) {
        if (!llm.isConfigured()) {
            return null;
        }
        StringBuilder p = new StringBuilder();
        p.append("【植物】").append(speciesName == null ? "未知" : speciesName).append('\n');
        p.append("【已确定的诊断】").append(best.nameCn())
                .append("（模型置信度 ").append(String.format("%.0f%%", best.confidence() * 100)).append("）\n");
        if (info != null) {
            p.append("【知识库·症状】").append(abbrev(info.getSymptoms(), 200)).append('\n');
            if (info.getCauses() != null) {
                p.append("【知识库·成因】").append(abbrev(info.getCauses(), 160)).append('\n');
            }
            if (info.getTreatment() != null) {
                p.append("【知识库·处置】").append(abbrev(info.getTreatment(), 300)).append('\n');
            }
        }
        if (question != null && !question.isBlank()) {
            p.append("【用户补充】").append(question.trim()).append('\n');
        }
        p.append("""

                【任务】用通俗的话给用户讲清楚，严格按 JSON 输出：
                {
                  "treatment": ["先做什么", "再做什么", "什么时候需要用药/换盆/丢弃", "3-5 条，按优先级排序"],
                  "prevention": ["以后怎么避免", "2-3 条"],
                  "explain": "一两句话说明为什么会得这个病（结合图片和成因）"
                }
                """);
        LlmClient.Result r = llm.chatVision(image, "image/jpeg", EXPLAIN_SYSTEM, p.toString());
        if (!r.ok()) {
            log.warn("大模型讲解失败：{}", r.error());
            return null;
        }
        return parseText(r.content(), "treatment");
    }

    // ------------------------------------------------------------------
    // 链路 2：Top-3 的鉴别要点
    // ------------------------------------------------------------------

    private String candidateExplain(byte[] image, String speciesName,
                                    List<AiDiagnosisVO.Item> topK, String question) {
        if (!llm.isConfigured()) {
            return null;
        }
        StringBuilder p = new StringBuilder();
        p.append("【植物】").append(speciesName == null ? "未知" : speciesName).append('\n');
        p.append("【模型给出的候选，按可能性排序】\n");
        for (int i = 0; i < topK.size(); i++) {
            AiDiagnosisVO.Item it = topK.get(i);
            DiseaseInfoEntity info = findByCode(it.code());
            p.append(i + 1).append(". ").append(it.nameCn())
                    .append("（").append(String.format("%.0f%%", it.confidence() * 100)).append("）");
            if (info != null) {
                p.append("：").append(abbrev(info.getSymptoms(), 80));
            }
            p.append('\n');
        }
        if (question != null && !question.isBlank()) {
            p.append("【用户补充】").append(question.trim()).append('\n');
        }
        p.append("""

                【任务】用户需要自己确认是哪一种。请给出可操作的鉴别方法，严格按 JSON 输出：
                {"treatment": ["针对每一种，教用户怎么用肉眼区分（看哪里、看什么特征）",
                               "每一条都要具体，例如'翻开叶背看有没有橙褐色粉状小点'",
                               "3-4 条"]}
                """);
        LlmClient.Result r = llm.chatVision(image, "image/jpeg", EXPLAIN_SYSTEM, p.toString());
        if (!r.ok()) {
            return null;
        }
        return parseText(r.content(), "treatment");
    }

    // ------------------------------------------------------------------
    // 链路 3：未覆盖物种，只判大类
    // ------------------------------------------------------------------

    private AiDiagnosisVO categoryOnly(byte[] image, String speciesName, String question,
                                       List<AiDiagnosisVO.Item> topK) {
        if (!llm.isConfigured()) {
            return unavailable("该物种未被识别模型覆盖，且未配置大模型，无法进一步判断", topK);
        }
        StringBuilder p = new StringBuilder();
        p.append("【植物】").append(speciesName == null ? "未知" : speciesName).append('\n');
        p.append("【说明】本地识别模型没有收录该植物的病害，你只能判断大类，不能给具体病名。\n");
        if (question != null && !question.isBlank()) {
            p.append("【用户补充】").append(question.trim()).append('\n');
        }
        p.append("""

                【任务】严格按 JSON 输出：
                {
                  "category": "FUNGAL/BACTERIAL/PEST/PHYSIOLOGICAL/VIRAL/UNKNOWN",
                  "evidence": ["你在图片里实际看到的症状，2-4 条，写清颜色/形状/位置"],
                  "treatment": ["通用处置建议，3-5 条；涉及用药只说'按产品标签稀释'"],
                  "prevention": ["预防，2-3 条"],
                  "confidence": 0.0,
                  "needMoreInfo": false,
                  "followUpQuestion": "需要用户补充什么才能更准"
                }
                """);
        LlmClient.Result r = llm.chatVision(image, "image/jpeg", CATEGORY_SYSTEM, p.toString());
        if (!r.ok()) {
            return unavailable("大模型调用失败：" + r.error(), topK);
        }
        try {
            JsonNode n = JSON.readTree(jsonOf(r.content()));
            return new AiDiagnosisVO(
                    "VLM_CATEGORY", null,
                    n.path("category").asText("UNKNOWN"),
                    n.path("confidence").asDouble(0), false,
                    strList(n.path("evidence")),
                    List.of(),
                    strList(n.path("treatment")),
                    strList(n.path("prevention")),
                    n.path("needMoreInfo").asBoolean(false),
                    n.path("followUpQuestion").asText(""),
                    DISCLAIMER,
                    "该物种未被自训模型覆盖；结论仅为病害大类，具体病名需人工确认。模型：" + llm.visionModel(),
                    topK);
        } catch (Exception e) {
            log.warn("解析大类结果失败：{}", e.getMessage());
            return unavailable("大模型返回无法解析", topK);
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static String jsonOf(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            int end = t.lastIndexOf("```");
            if (end > 0) {
                t = t.substring(0, end);
            }
        }
        int s = t.indexOf('{');
        int e = t.lastIndexOf('}');
        return (s >= 0 && e > s) ? t.substring(s, e + 1) : "{}";
    }

    /** 从 JSON 里取某个数组字段，按行返回（便于前端按步骤渲染） */
    private static String parseText(String raw, String field) {
        try {
            JsonNode n = JSON.readTree(jsonOf(raw));
            List<String> v = strList(n.path(field));
            return String.join("\n", v);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> strList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode x : arr) {
                String v = x.asText("").trim();
                if (!v.isEmpty()) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    private static final String DISCLAIMER =
            "AI 结论仅供参考，不构成农药使用指导。用药请遵循产品标签与当地规定。";

    private AiDiagnosisVO unavailable(String reason, List<AiDiagnosisVO.Item> topK) {
        return new AiDiagnosisVO("UNAVAILABLE", null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                true, "可以先用文字描述症状（叶片颜色、斑点位置、是否浇水过频），我再帮你判断。",
                DISCLAIMER, reason, topK);
    }

    private PlantSpeciesEntity resolveSpecies(Long userId, AiDiagnosisRequest req, PlantPhotoEntity photo) {
        Long sid = req.speciesId();
        if (sid == null) {
            Long pid = req.userPlantId() != null ? req.userPlantId() : photo.getUserPlantId();
            if (pid != null) {
                UserPlantEntity up = userPlantMapper.selectById(pid);
                if (up != null && (up.getUserId() == null || up.getUserId().equals(userId))) {
                    sid = up.getSpeciesId();
                }
            }
        }
        return sid == null ? null : speciesMapper.selectById(sid);
    }

    private DiseaseInfoEntity findByCode(String code) {
        if (code == null) {
            return null;
        }
        List<DiseaseInfoEntity> list = diseaseInfoMapper.selectList(
                Wrappers.<DiseaseInfoEntity>lambdaQuery().eq(DiseaseInfoEntity::getCode, code).last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private static String abbrev(String s, int n) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\n", " ").trim();
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }

    private static List<String> splitLines(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
