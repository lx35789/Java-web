package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.common.PageResult;
import com.greenthumb.dto.DiagnosisRequest;
import com.greenthumb.dto.SymptomDiagnosisRequest;
import com.greenthumb.entity.CauseInfoEntity;
import com.greenthumb.entity.DiseaseInfoEntity;
import com.greenthumb.entity.DiseaseRecordEntity;
import com.greenthumb.entity.PlantPhotoEntity;
import com.greenthumb.entity.SymptomRuleEntity;
import com.greenthumb.entity.UserPlantEntity;
import com.greenthumb.mapper.CauseInfoMapper;
import com.greenthumb.mapper.DiseaseInfoMapper;
import com.greenthumb.mapper.DiseaseRecordMapper;
import com.greenthumb.mapper.SymptomRuleMapper;
import com.greenthumb.mapper.UserPlantMapper;
import com.greenthumb.vo.DiagnosisSummaryVO;
import com.greenthumb.vo.DiseaseDiagnosisVO;
import com.greenthumb.vo.SymptomDiagnosisVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 诊断服务：把 AI 模型 / 症状规则 的结果，翻译成前端能直接渲染的结构，并落库。
 * <p>
 * 两条路径：
 * 1. 病理性（DISEASE）：照片 → Python AI 服务 → 病害类别 → 查 disease_info → 处理建议
 * 2. 生理性（SYMPTOM）：勾选的症状 → symptom_rule 累加权重 → cause_info → 病因与对策
 * <p>
 * 两条都遵守同一条底线：**信息不足时不给结论**，而是告诉用户"还需要什么"。
 */
@Service
public class DiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> VALID_STATUS = Set.of("OPEN", "TREATING", "RESOLVED");
    private static final String DISCLAIMER = "以上为园艺级处理建议，不构成农药使用指导。";
    private static final double LOW_CONFIDENCE = 0.5d;
    private static final int TOP_K = 3;

    private final PhotoService photoService;
    private final AiClientService aiClient;
    private final DiseaseInfoMapper diseaseInfoMapper;
    private final DiseaseRecordMapper diseaseRecordMapper;
    private final SymptomRuleMapper symptomRuleMapper;
    private final CauseInfoMapper causeInfoMapper;
    private final UserPlantMapper userPlantMapper;
    private final ObjectMapper json;

    public DiagnosisService(PhotoService photoService,
                            AiClientService aiClient,
                            DiseaseInfoMapper diseaseInfoMapper,
                            DiseaseRecordMapper diseaseRecordMapper,
                            SymptomRuleMapper symptomRuleMapper,
                            CauseInfoMapper causeInfoMapper,
                            UserPlantMapper userPlantMapper,
                            ObjectMapper json) {
        this.photoService = photoService;
        this.aiClient = aiClient;
        this.diseaseInfoMapper = diseaseInfoMapper;
        this.diseaseRecordMapper = diseaseRecordMapper;
        this.symptomRuleMapper = symptomRuleMapper;
        this.causeInfoMapper = causeInfoMapper;
        this.userPlantMapper = userPlantMapper;
        this.json = json;
    }

    /* ==================== 1. 病理性：病害识别 ==================== */

    @Transactional
    public DiseaseDiagnosisVO diagnoseDisease(Long userId, DiagnosisRequest request) {
        if (request == null || request.photoId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 photoId");
        }
        PlantPhotoEntity photo = photoService.getOwned(userId, request.photoId());
        Long userPlantId = resolvePlantId(userId, request.userPlantId(), photo);

        byte[] bytes = photoService.readBytes(photo);
        AiClientService.Prediction pred =
                aiClient.predictDisease(bytes, photoService.filenameOf(photo), TOP_K);

        // —— 模型不可用：不落库、不报错，把原因如实告诉用户 ——
        if (!pred.available()) {
            log.info("病害识别降级：{}", pred.reason());
            return new DiseaseDiagnosisVO(
                    null, "DISEASE", "NONE", null, List.of(),
                    null, null, null,
                    "暂时无法识别这张照片（" + (pred.reason() == null ? "模型未就绪" : pred.reason())
                            + "）。可以先到「AI 问答」里描述一下症状。",
                    false, pred.reason(), pred.latencyMs(), null);
        }

        // —— 查知识库：一个 code 查不到很正常（模型比知识库更新得快），不能当成错误 ——
        List<String> codes = pred.topK().stream().map(AiClientService.Item::code).toList();
        Map<String, DiseaseInfoEntity> infoMap = loadInfoByCodes(codes);

        AiClientService.Item top1 = pred.topK().get(0);
        DiseaseInfoEntity info = infoMap.get(top1.code());
        double confidence = round4(top1.confidence());

        List<DiseaseDiagnosisVO.Item> items = new ArrayList<>();
        int rank = 1;
        for (AiClientService.Item it : pred.topK()) {
            DiseaseInfoEntity di = infoMap.get(it.code());
            items.add(new DiseaseDiagnosisVO.Item(
                    rank++, it.code(), displayName(it, di), round4(it.confidence())));
        }

        DiseaseRecordEntity rec = new DiseaseRecordEntity();
        rec.setUserId(userId);
        rec.setUserPlantId(userPlantId);
        rec.setPhotoId(photo.getId());
        rec.setProvider("SELF_MODEL");
        rec.setDiseaseInfoId(info == null ? null : info.getId());
        rec.setPredictedCode(top1.code());
        rec.setConfidence(BigDecimal.valueOf(confidence));
        rec.setTopK(toJson(items));
        rec.setDiagnosisType("DISEASE");
        rec.setSuggestion(info == null ? null : info.getTreatment());
        rec.setStatus("OPEN");
        rec.setCreatedAt(LocalDateTime.now());
        rec.setDeleted(0);
        diseaseRecordMapper.insert(rec);

        DiseaseDiagnosisVO.Predicted predicted = new DiseaseDiagnosisVO.Predicted(
                top1.code(),
                displayName(top1, info),
                info == null ? null : info.getNameEn(),
                info == null ? null : info.getCategory(),
                confidence,
                info == null ? null : info.getSeverity());

        return new DiseaseDiagnosisVO(
                rec.getId(), "DISEASE", "SELF_MODEL", predicted, items,
                info == null ? null : info.getSymptoms(),
                info == null
                        ? "模型识别到「" + displayName(top1, null) + "」，但知识库还没有这一类别的处理建议。"
                        + "建议先隔离观察，并到「AI 问答」里补充描述。"
                        : info.getTreatment(),
                info == null ? null : info.getPrevention(),
                DISCLAIMER,
                true, null, pred.latencyMs(), confidence < LOW_CONFIDENCE);
    }

    /* ==================== 2. 生理性：症状判因 ==================== */

    @Transactional
    public SymptomDiagnosisVO diagnoseSymptom(Long userId, SymptomDiagnosisRequest request) {
        if (request == null || request.symptomCodes() == null || request.symptomCodes().isEmpty()) {
            throw new BizException(ErrorCode.SYMPTOM_INSUFFICIENT, "请至少选择一个症状");
        }
        Long userPlantId = request.userPlantId();
        if (userPlantId != null) {
            UserPlantEntity plant = userPlantMapper.selectById(userPlantId);
            if (plant == null || !plant.getUserId().equals(userId)) {
                throw new BizException(ErrorCode.NOT_FOUND, "植物不存在");
            }
        }

        List<String> codes = request.symptomCodes().stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        List<SymptomRuleEntity> rules = symptomRuleMapper.selectList(
                new LambdaQueryWrapper<SymptomRuleEntity>()
                        .in(SymptomRuleEntity::getSymptomCode, codes)
                        .eq(SymptomRuleEntity::getEnabled, 1));

        Map<String, Object> ctx = buildContext(userId, userPlantId, request.context());

        // 权重累加：同一病因被多条命中的规则累加，这是规则库设计的核心
        Map<String, Double> score = new LinkedHashMap<>();
        Map<String, String> explain = new HashMap<>();
        for (SymptomRuleEntity rule : rules) {
            if (!conditionMatches(rule.getConditionJson(), ctx)) {
                continue;
            }
            double w = rule.getWeight() == null ? 0d : rule.getWeight().doubleValue();
            score.merge(rule.getCauseCode(), w, Double::sum);
            if (rule.getExplainTpl() != null && !rule.getExplainTpl().isBlank()) {
                explain.putIfAbsent(rule.getCauseCode(), fill(rule.getExplainTpl(), ctx));
            }
        }

        // —— 规则库没配 / 一条都没命中：明确说"信息不足"，而不是硬猜 ——
        if (score.isEmpty()) {
            log.info("症状判因无命中：symptoms={} context={}", codes, ctx);
            return new SymptomDiagnosisVO(
                    null, "SYMPTOM", "LOW", List.of(), true, followUpQuestions(ctx),
                    "目前的信息还不足以判断原因。补充上面的信息会更准确，你也可以直接去 AI 问答里描述情况。");
        }

        double total = score.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map.Entry<String, Double>> ranked = score.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed())
                .limit(TOP_K)
                .toList();

        // 证据强度用**原始权重和**衡量，而不是归一化后的占比：
        // 若只命中一条 0.20 的弱规则，占比会是 100%，但证据其实很弱。
        double topRaw = ranked.isEmpty() ? 0d : ranked.get(0).getValue();

        Map<String, CauseInfoEntity> causeMap = causeInfoMapper.selectList(
                        new LambdaQueryWrapper<CauseInfoEntity>()
                                .in(CauseInfoEntity::getCauseCode, ranked.stream().map(Map.Entry::getKey).toList()))
                .stream().collect(Collectors.toMap(CauseInfoEntity::getCauseCode, c -> c, (a, b) -> a));

        List<SymptomDiagnosisVO.Candidate> candidates = new ArrayList<>();
        int rank = 1;
        double top1 = 0d;
        for (Map.Entry<String, Double> e : ranked) {            CauseInfoEntity cause = causeMap.get(e.getKey());
            double conf = round4(total <= 0 ? 0
                    : (e.getValue() / total) * Math.min(1.0, topRaw));
            if (rank == 1) {
                top1 = conf;
            }
            // 置信度 = 相对占比 × 证据强度：
            // 若只命中一条 0.30 的弱规则，占比虽是 100%，展示成"浇水过多 30%"才与
            // confidenceLevel=LOW 的结论自洽，不会出现"100% 但信息不足"的矛盾观感。
            candidates.add(new SymptomDiagnosisVO.Candidate(
                    rank++,
                    e.getKey(),
                    cause == null ? e.getKey() : cause.getNameCn(),
                    cause == null ? null : cause.getCategory(),
                    conf,
                    cause == null ? "MEDIUM" : cause.getUrgency(),
                    explain.getOrDefault(e.getKey(),
                            cause == null ? null : cause.getDescription()),
                    cause == null ? null : cause.getActionPlan()));
        }

        String level = topRaw >= 0.7 ? "HIGH" : (topRaw >= 0.4 ? "MEDIUM" : "LOW");
        boolean needMore = "LOW".equals(level);

        DiseaseRecordEntity rec = new DiseaseRecordEntity();
        rec.setUserId(userId);
        rec.setUserPlantId(userPlantId);
        rec.setPhotoId(request.photoId());
        rec.setProvider("RULE");
        rec.setPredictedCode(candidates.get(0).code());
        rec.setConfidence(BigDecimal.valueOf(top1));
        rec.setTopK(toJson(candidates));
        rec.setDiagnosisType("SYMPTOM");
        rec.setSymptomTags(toJson(codes));
        rec.setSuggestion(candidates.get(0).actionPlan());
        rec.setStatus("OPEN");
        rec.setCreatedAt(LocalDateTime.now());
        rec.setDeleted(0);
        diseaseRecordMapper.insert(rec);

        return new SymptomDiagnosisVO(
                rec.getId(), "SYMPTOM", level, candidates, needMore,
                needMore ? followUpQuestions(ctx) : List.of(),
                needMore ? "信息还不够充分，补充上面的信息会更准确。" : null);
    }

    /* ==================== 3. 查询 / 状态 ==================== */

    public DiseaseDiagnosisVO get(Long userId, Long recordId) {
        DiseaseRecordEntity rec = requireOwned(userId, recordId);

        if ("SYMPTOM".equals(rec.getDiagnosisType())) {
            // 症状路径的记录也用同一个 VO 返回，前端按 diagnosisType 分支渲染
            List<DiseaseDiagnosisVO.Item> items = readTopK(rec);
            return new DiseaseDiagnosisVO(rec.getId(), "SYMPTOM", rec.getProvider(), null, items,
                    null, rec.getSuggestion(), null, null, true, null, null, false);
        }

        Map<String, DiseaseInfoEntity> infoMap = rec.getPredictedCode() == null
                ? Map.of() : loadInfoByCodes(List.of(rec.getPredictedCode()));
        DiseaseInfoEntity info = infoMap.get(rec.getPredictedCode());
        List<DiseaseDiagnosisVO.Item> items = readTopK(rec);
        Double conf = rec.getConfidence() == null ? null : rec.getConfidence().doubleValue();

        DiseaseDiagnosisVO.Predicted predicted = rec.getPredictedCode() == null ? null
                : new DiseaseDiagnosisVO.Predicted(
                rec.getPredictedCode(),
                info == null ? rec.getPredictedCode() : info.getNameCn(),
                info == null ? null : info.getNameEn(),
                info == null ? null : info.getCategory(),
                conf,
                info == null ? null : info.getSeverity());

        return new DiseaseDiagnosisVO(rec.getId(), "DISEASE", rec.getProvider(), predicted, items,
                info == null ? null : info.getSymptoms(),
                info == null ? rec.getSuggestion() : info.getTreatment(),
                info == null ? null : info.getPrevention(),
                DISCLAIMER, true, null, null,
                conf != null && conf < LOW_CONFIDENCE);
    }

    public PageResult<DiagnosisSummaryVO> list(Long userId, String diagnosisType,
                                               long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 100);

        Page<DiseaseRecordEntity> mp = new Page<>(p, s);
        LambdaQueryWrapper<DiseaseRecordEntity> w = new LambdaQueryWrapper<>();
        w.eq(DiseaseRecordEntity::getUserId, userId)
                .eq(DiseaseRecordEntity::getDeleted, 0)
                .eq(diagnosisType != null && !diagnosisType.isBlank(),
                        DiseaseRecordEntity::getDiagnosisType,
                        diagnosisType == null ? null : diagnosisType.trim().toUpperCase(Locale.ROOT))
                .orderByDesc(DiseaseRecordEntity::getCreatedAt);
        Page<DiseaseRecordEntity> result = diseaseRecordMapper.selectPage(mp, w);

        List<DiseaseRecordEntity> rows = result.getRecords();
        Map<Long, String> nicknames = loadNicknames(userId, rows);
        Map<String, DiseaseInfoEntity> infoMap = loadInfoByCodes(rows.stream()
                .map(DiseaseRecordEntity::getPredictedCode).filter(c -> c != null).distinct().toList());

        List<DiagnosisSummaryVO> list = new ArrayList<>();
        for (DiseaseRecordEntity r : rows) {
            DiseaseInfoEntity info = infoMap.get(r.getPredictedCode());
            String summary = info != null ? info.getNameCn()
                    : (r.getPredictedCode() == null ? "未得出结论" : r.getPredictedCode());
            list.add(new DiagnosisSummaryVO(
                    r.getId(),
                    r.getUserPlantId(),
                    r.getUserPlantId() == null ? null : nicknames.get(r.getUserPlantId()),
                    r.getDiagnosisType(),
                    r.getCreatedAt() == null ? null : r.getCreatedAt().format(DT),
                    summary,
                    r.getConfidence() == null ? null : r.getConfidence().doubleValue(),
                    r.getStatus(),
                    r.getPredictedCode()));
        }
        return PageResult.of(list, result.getTotal(), p, s);
    }

    @Transactional
    public DiagnosisSummaryVO updateStatus(Long userId, Long recordId, String status) {
        DiseaseRecordEntity rec = requireOwned(userId, recordId);
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUS.contains(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "status 只能是 OPEN / TREATING / RESOLVED");
        }
        rec.setStatus(st);
        rec.setResolvedAt("RESOLVED".equals(st) ? LocalDateTime.now() : null);
        diseaseRecordMapper.updateById(rec);

        DiseaseInfoEntity info = rec.getPredictedCode() == null ? null
                : diseaseInfoMapper.selectOne(new LambdaQueryWrapper<DiseaseInfoEntity>()
                .eq(DiseaseInfoEntity::getCode, rec.getPredictedCode()).last("limit 1"));
        return new DiagnosisSummaryVO(rec.getId(), rec.getUserPlantId(), null,
                rec.getDiagnosisType(), rec.getCreatedAt() == null ? null : rec.getCreatedAt().format(DT),
                info == null ? rec.getPredictedCode() : info.getNameCn(),
                rec.getConfidence() == null ? null : rec.getConfidence().doubleValue(),
                rec.getStatus(), rec.getPredictedCode());
    }

    /* ==================== 内部工具 ==================== */

    private DiseaseRecordEntity requireOwned(Long userId, Long recordId) {
        DiseaseRecordEntity rec = recordId == null ? null : diseaseRecordMapper.selectById(recordId);
        if (rec == null || !userId.equals(rec.getUserId()) || Integer.valueOf(1).equals(rec.getDeleted())) {
            throw new BizException(ErrorCode.NOT_FOUND, "诊断记录不存在");
        }
        return rec;
    }

    /** 传了 userPlantId 就用它（并校验归属），否则回落到照片上挂的植物 */
    private Long resolvePlantId(Long userId, Long requested, PlantPhotoEntity photo) {
        if (requested != null) {
            UserPlantEntity plant = userPlantMapper.selectById(requested);
            if (plant == null || !plant.getUserId().equals(userId)) {
                throw new BizException(ErrorCode.NOT_FOUND, "植物不存在");
            }
            return requested;
        }
        return photo.getUserPlantId();
    }

    private Map<String, DiseaseInfoEntity> loadInfoByCodes(List<String> codes) {
        List<String> valid = codes == null ? List.of()
                : codes.stream().filter(c -> c != null && !c.isBlank()).distinct().toList();
        if (valid.isEmpty()) {
            return Map.of();
        }
        return diseaseInfoMapper.selectList(new LambdaQueryWrapper<DiseaseInfoEntity>()
                        .in(DiseaseInfoEntity::getCode, valid))
                .stream().collect(Collectors.toMap(DiseaseInfoEntity::getCode, i -> i, (a, b) -> a));
    }

    private Map<Long, String> loadNicknames(Long userId, List<DiseaseRecordEntity> rows) {
        List<Long> ids = rows.stream().map(DiseaseRecordEntity::getUserPlantId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (UserPlantEntity p : userPlantMapper.selectList(
                new LambdaQueryWrapper<UserPlantEntity>()
                        .eq(UserPlantEntity::getUserId, userId)
                        .in(UserPlantEntity::getId, ids))) {
            map.put(p.getId(), p.getNickname());
        }
        return map;
    }

    private static String displayName(AiClientService.Item it, DiseaseInfoEntity info) {
        if (info != null && info.getNameCn() != null) {
            return info.getNameCn();
        }
        if (it.nameCn() != null && !it.nameCn().isBlank()) {
            return it.nameCn();
        }
        // 模型只给了英文类别码时，退化成"物种·病害"的可读形式
        String code = it.code() == null ? "未知" : it.code().replace('_', ' ');
        return code;
    }

    private List<DiseaseDiagnosisVO.Item> readTopK(DiseaseRecordEntity rec) {
        List<DiseaseDiagnosisVO.Item> items = new ArrayList<>();
        if (rec.getTopK() == null || rec.getTopK().isBlank()) {
            return items;
        }
        try {
            JsonNode arr = json.readTree(rec.getTopK());
            int rank = 1;
            for (JsonNode n : arr) {
                String code = n.path("code").asText(null);
                DiseaseInfoEntity info = loadInfoByCodes(List.of(code == null ? "" : code)).get(code);
                items.add(new DiseaseDiagnosisVO.Item(
                        n.path("rank").asInt(rank),
                        code,
                        info == null ? n.path("nameCn").asText(code) : info.getNameCn(),
                        n.path("confidence").asDouble(0d)));
                rank++;
            }
        } catch (Exception e) {
            log.warn("解析 topK 失败：{}", rec.getTopK());
        }
        return items;
    }

    /**
     * 条件匹配（多字段 AND）。**所有条件都必须被上下文确认**才能命中：
     * <ul>
     *   <li>字段在 context 里有值 → 必须相等（或满足 {op,value} 数值比较），否则整条规则不成立</li>
     *   <li>字段在 context 里没有 → 无法确认，整条规则不成立（宁可少判，不可误判）</li>
     *   <li>condition 写成 {} 或 null → 无条件规则，直接命中</li>
     * </ul>
     * 上下文由 {@link #buildContext} 补齐（光照、盆材质、季节来自档案与日期），
     * 浇水频率等字段等养护模块落地后再接。
     */
    private static boolean conditionMatches(String conditionJson, Map<String, Object> ctx) {
        if (conditionJson == null || conditionJson.isBlank() || "{}".equals(conditionJson.trim())) {
            return true;
        }
        try {
            ObjectMapper m = new ObjectMapper();
            JsonNode cond = m.readTree(conditionJson);
            var it = cond.fields();
            while (it.hasNext()) {
                var e = it.next();
                Object actual = ctx.get(e.getKey());
                if (actual == null) {
                    // 上下文里没有这个字段 ⇒ 该条件无法确认 ⇒ 整条规则不成立。
                    // 反过来（缺失就放行）后果很严重：所有无法验证的规则都按满分计入，
                    // 既稀释了真正有证据的病因，也会仅凭一条 "ANY" 规则就给出 100% 的假结论。
                    return false;
                }
                JsonNode expect = e.getValue();
                if (expect.isObject() && expect.has("op")) {
                    double want = expect.path("value").asDouble();
                    double got = Double.parseDouble(String.valueOf(actual));
                    String op = expect.path("op").asText();
                    boolean ok = switch (op) {
                        case ">=" -> got >= want;
                        case ">" -> got > want;
                        case "<=" -> got <= want;
                        case "<" -> got < want;
                        case "==" -> got == want;
                        default -> true;
                    };
                    if (!ok) {
                        return false;
                    }
                } else if (!expect.asText().equalsIgnoreCase(String.valueOf(actual))) {
                    return false;
                }
            }
        } catch (Exception e) {
            return true;    // 条件写错了不要连累诊断，放行
        }
        return true;
    }

    private static String fill(String tpl, Map<String, Object> ctx) {
        String out = tpl;
        for (Map.Entry<String, Object> e : ctx.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return out;
    }

    /**
     * 补齐判因上下文。前端**只负责回答"看得见的现象"**，其余由后端推算
     * （这是 docs/03 第 9 节的设计：叶位一项就能把 10 个候选压到 3 个）。
     * <p>
     * 已实现：
     * light_level  ← user_plant.light_level
     * pot_material ← user_plant.pot_material
     * season       ← 当前日期
     * <p>
     * TODO（等养护计划模块落地后接上，届时这几条规则才会真正发力）：
     * watering_ratio ← care_log 近 14 天 WATER 次数 ÷ (14 / care_plan.effective_interval_days)
     * fertilizing    ← care_log 的 FERTILIZE 记录（NONE / RECENT / HEAVY）
     * humidity/temp  ← user_environment + 天气接口
     * 目前这些字段缺失时，规则按"条件未确认"放行，不会阻断判因。
     */
    private Map<String, Object> buildContext(Long userId, Long userPlantId, Map<String, Object> provided) {
        Map<String, Object> ctx = new HashMap<>();
        if (provided != null) {
            provided.forEach((k, v) -> {
                if (k != null && v != null) {
                    ctx.put(k, v);
                    // 前端按 camelCase 传（leafPosition），而规则条件里写的是 snake_case（leaf_position）。
                    // 这里补一份 snake_case 键 —— 否则字段名对不上会被当成"上下文缺失"，
                    // 表现为「新叶发黄」和「老叶发黄」返回一模一样的结果，且完全不报错。
                    ctx.putIfAbsent(toSnake(k), v);
                }
            });
        }
        ctx.putIfAbsent("season", seasonOf(LocalDate.now()));

        if (userPlantId == null) {
            return ctx;
        }
        UserPlantEntity plant = userPlantMapper.selectById(userPlantId);
        if (plant != null && userId.equals(plant.getUserId())) {
            if (plant.getLightLevel() != null) {
                ctx.putIfAbsent("light_level", plant.getLightLevel());
            }
            if (plant.getPotMaterial() != null) {
                ctx.putIfAbsent("pot_material", plant.getPotMaterial());
            }
        }
        return ctx;
    }

    private static String toSnake(String k) {
        StringBuilder sb = new StringBuilder(k.length() + 4);
        for (char c : k.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String seasonOf(LocalDate d) {
        return switch (d.getMonthValue()) {
            case 3, 4, 5 -> "SPRING";
            case 6, 7, 8 -> "SUMMER";
            case 9, 10, 11 -> "AUTUMN";
            default -> "WINTER";
        };
    }

    /**
     * 追问清单只问**还不知道**的字段——已经能从档案推算出来的就别再问用户，
     * 这是"后端自动补上下文"这个设计的意义所在。
     */
    private static List<String> followUpQuestions(Map<String, Object> ctx) {
        List<String> qs = new ArrayList<>();
        if (!ctx.containsKey("leaf_position")) {
            qs.add("发黄是从下面的老叶开始，还是上面的新叶先黄？");
        }
        if (!ctx.containsKey("watering_ratio")) {
            qs.add("最近两周大概浇了几次水？");
        }
        if (!ctx.containsKey("light_level")) {
            qs.add("这盆植物摆放的位置光线怎么样？");
        }
        if (!ctx.containsKey("humidity")) {
            qs.add("房间里干燥吗（有没有开空调或暖气）？");
        }
        if (qs.isEmpty()) {
            qs.add("最近有没有换盆、换位置或者施肥？");
        }
        return qs;
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("序列化失败：{}", e.getMessage());
            return null;
        }
    }

    private static double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
