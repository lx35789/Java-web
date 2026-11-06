package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.common.PageResult;
import com.greenthumb.common.SpeciesKeys;
import com.greenthumb.dto.DiagnosisRequest;
import com.greenthumb.dto.SymptomDiagnosisRequest;
import com.greenthumb.entity.CauseInfoEntity;
import com.greenthumb.entity.DiseaseInfoEntity;
import com.greenthumb.entity.DiseaseRecordEntity;
import com.greenthumb.entity.PlantPhotoEntity;
import com.greenthumb.entity.PlantSpeciesEntity;
import com.greenthumb.entity.SymptomRuleEntity;
import com.greenthumb.entity.UserPlantEntity;
import com.greenthumb.mapper.CauseInfoMapper;
import com.greenthumb.mapper.DiseaseInfoMapper;
import com.greenthumb.mapper.DiseaseRecordMapper;
import com.greenthumb.mapper.PlantSpeciesMapper;
import com.greenthumb.mapper.SymptomRuleMapper;
import com.greenthumb.mapper.UserPlantMapper;
import com.greenthumb.service.llm.LlmClient;
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
    /** 该物种支持的类别数不超过此值时，返回中追加覆盖度警告（防漏诊） */
    private static final int NARROW_COVERAGE_MAX = 3;
    /**
     * 自训模型的最低信任门槛：低于此值不当作结论，转兜底链路复核。
     * <p>
     * 为什么不再往下调：实测芦荟炭疽病照片，专用模型给「健康 36.4%」，
     * 30% 级别的输出基本等同于瞎猜；把它展示成结论比说"判断不了"更糟。
     */
    private static final double MIN_TRUST = 0.5d;
    private static final int TOP_K = 3;

    private final PhotoService photoService;
    private final AiClientService aiClient;
    private final DiseaseInfoMapper diseaseInfoMapper;
    private final DiseaseRecordMapper diseaseRecordMapper;
    private final SymptomRuleMapper symptomRuleMapper;
    private final CauseInfoMapper causeInfoMapper;
    private final UserPlantMapper userPlantMapper;
    private final PlantSpeciesMapper plantSpeciesMapper;
    private final PlantNetDiseaseClient plantNetDisease;
    private final LlmClient llm;
    private final ObjectMapper json;

    public DiagnosisService(PhotoService photoService,
                            AiClientService aiClient,
                            DiseaseInfoMapper diseaseInfoMapper,
                            DiseaseRecordMapper diseaseRecordMapper,
                            SymptomRuleMapper symptomRuleMapper,
                            CauseInfoMapper causeInfoMapper,
                            UserPlantMapper userPlantMapper,
                            PlantSpeciesMapper plantSpeciesMapper,
                            PlantNetDiseaseClient plantNetDisease,
                            LlmClient llm,
                            ObjectMapper json) {
        this.photoService = photoService;
        this.aiClient = aiClient;
        this.diseaseInfoMapper = diseaseInfoMapper;
        this.diseaseRecordMapper = diseaseRecordMapper;
        this.symptomRuleMapper = symptomRuleMapper;
        this.causeInfoMapper = causeInfoMapper;
        this.userPlantMapper = userPlantMapper;
        this.plantSpeciesMapper = plantSpeciesMapper;
        this.plantNetDisease = plantNetDisease;
        this.llm = llm;
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

        // 按物种路由：芦荟有专用 5 类模型，不传 key 会回退通用模型
        //（同一张芦荟图，专用模型 Top1 75%+，通用模型约 8%）
        // 优先用植株档案的物种；第 2 步时用户往往还没选植株，则用第 1 步识别出的物种名
        String speciesKey = resolveSpeciesKey(userPlantId);
        if (speciesKey == null) {
            speciesKey = SpeciesKeys.keyOf(request.speciesName());
        }

        byte[] bytes = photoService.readBytes(photo);
        AiClientService.Prediction pred =
                aiClient.predictDisease(bytes, photoService.filenameOf(photo), TOP_K, speciesKey);

        // —— 模型不可用 / 该物种下没有可信结论：不落库，走兜底链路 ——
        if (!pred.available()) {
            log.info("病害识别降级：{}", pred.reason());
            return noConclusionFallback(bytes, photo, speciesKey, pred.reason(), pred.latencyMs(), pred.topK());
        }

        // —— 模型可用，但在该物种下没有明确结论 ——
        // 典型场景：AI 服务做了物种一致性过滤，把「绿萝照片被判成月季霜霉病」的结论剔掉了，
        // 同时该物种的概率总量又不足以支撑任何结论。此时必须如实说"判断不了"，
        // 绝不能退回去用其他物种的病名，否则用户看到的会是「绿萝得了月季霜霉病」。
        if (pred.topK() == null || pred.topK().isEmpty()) {
            log.info("病害识别无结论（物种={}）：{}", speciesKey, pred.reason());
            return noConclusionFallback(bytes, photo, speciesKey, pred.reason(), pred.latencyMs(), pred.topK());
        }
        // —— 有候选，但置信度过低 ——
        // 实测：一张芦荟炭疽病的照片，专用模型给出「芦荟·健康 36.4% / 锈病 33.0%」，
        // 真值炭疽病根本没进前二。这种结果展示成"结论"会直接误导用户，
        // 所以低于信任门槛时和"没结论"同等处理，交给兜底链路复核。
        double topConfidence = pred.topK().get(0).confidence();
        if (topConfidence < MIN_TRUST) {
            log.info("自训模型置信度不足（{} < {}），转兜底复核：{}",
                    String.format("%.2f", topConfidence), MIN_TRUST, pred.topK().get(0).code());
            return noConclusionFallback(bytes, photo, speciesKey,
                    String.format("自训模型置信度仅 %.0f%%", topConfidence * 100),
                    pred.latencyMs(), pred.topK());
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
                buildNote(pred),
                true, null, pred.latencyMs(), confidence < LOW_CONFIDENCE);
    }

    /**
     * 拼接给用户看的备注：免责声明 + 模型覆盖度警告。
     *
     * 为什么必须加覆盖度警告（实测依据）：
     *   仙人掌在本模型里只有 2 类（健康 / 胭脂虫）。拿一张“软腐”外观的图去测，
     *   模型给出「健康 88.7%」——置信度很高，结论却是漏诊。
     *   类别少的物种遇到未覆盖的病害必然这样，不告知用户就是误导。
     */
    private String buildNote(AiClientService.Prediction pred) {
        StringBuilder sb = new StringBuilder(DISCLAIMER);
        Integer cnt = pred.speciesClassCount();
        if (cnt != null && cnt <= NARROW_COVERAGE_MAX) {
            sb.append("\n本模型对该作物仅覆盖 ")
              .append(cnt)
              .append(" 种状态（")
              .append(coverageNames(pred.speciesClasses()))
              .append("）。若出现软腐、日灼、根腐等未覆盖症状，模型可能判为「健康」，")
              .append("请用「症状判因」补充描述。");
        }
        return sb.toString();
    }

    /** 把类别编码翻译成中文短名，避免正文里出现 Cactus_Healthy 这种字符串 */
    private String coverageNames(java.util.List<String> classes) {
        if (classes == null || classes.isEmpty()) {
            return "";
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String code : classes) {
            DiseaseInfoEntity d = diseaseInfoMapper.selectOne(
                    new LambdaQueryWrapper<DiseaseInfoEntity>()
                            .eq(DiseaseInfoEntity::getCode, code).last("limit 1"));
            String n = d == null ? null : d.getNameCn();
            if (n == null) {
                // 知识库没收录时用编码的后半段，总比整串编码好读
                int i = code.indexOf('_');
                n = i > 0 ? code.substring(i + 1).replace('_', ' ') : code;
            }
            // 「仙人掌·健康」在提示里写成「健康」更自然
            int dot = n.indexOf('·');
            out.add(dot >= 0 ? n.substring(dot + 1) : n);
        }
        return String.join("、", out);
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
        // 同一病因可能被多条规则命中 —— 依据与规则编号都要**全部保留**，
        // 前端会把它们逐条列出来，用户才能看懂「为什么是这个结论」
        Map<String, List<String>> evidenceMap = new HashMap<>();
        Map<String, List<Long>> ruleIdsMap = new HashMap<>();
        for (SymptomRuleEntity rule : rules) {
            if (!conditionMatches(rule.getConditionJson(), ctx)) {
                continue;
            }
            double w = rule.getWeight() == null ? 0d : rule.getWeight().doubleValue();
            score.merge(rule.getCauseCode(), w, Double::sum);
            if (rule.getExplainTpl() != null && !rule.getExplainTpl().isBlank()) {
                evidenceMap.computeIfAbsent(rule.getCauseCode(), k -> new ArrayList<>())
                        .add(fill(rule.getExplainTpl(), ctx));
            }
            ruleIdsMap.computeIfAbsent(rule.getCauseCode(), k -> new ArrayList<>())
                    .add(rule.getId());
        }

        // —— 规则库没配 / 一条都没命中：明确说"信息不足"，而不是硬猜 ——
        if (score.isEmpty()) {
            log.info("症状判因无命中：symptoms={} context={}", codes, ctx);
            return new SymptomDiagnosisVO(
                    null, "SYMPTOM", "LOW", List.of(), true, followUpQuestions(ctx),
                    "目前的信息还不足以判断原因。补充上面的信息会更准确，你也可以直接去 AI 问答里描述情况。",
                    null);
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
                    evidenceMap.getOrDefault(e.getKey(), List.of()),
                    splitActionPlan(cause == null ? null : cause.getActionPlan()),
                    ruleIdsMap.getOrDefault(e.getKey(), List.of())));
        }

        String level = topRaw >= 0.7 ? "HIGH" : (topRaw >= 0.4 ? "MEDIUM" : "LOW");
        boolean needMore = "LOW".equals(level);

        DiseaseRecordEntity rec = new DiseaseRecordEntity();
        rec.setUserId(userId);
        rec.setUserPlantId(userPlantId);
        rec.setPhotoId(request.photoId());
        rec.setProvider("RULE");
        rec.setPredictedCode(candidates.get(0).causeCode());
        rec.setConfidence(BigDecimal.valueOf(top1));
        rec.setTopK(toJson(candidates));
        rec.setDiagnosisType("SYMPTOM");
        rec.setSymptomTags(toJson(codes));
        rec.setSuggestion(String.join("\n", candidates.get(0).actionPlan()));
        rec.setStatus("OPEN");
        rec.setCreatedAt(LocalDateTime.now());
        rec.setDeleted(0);
        diseaseRecordMapper.insert(rec);

        return new SymptomDiagnosisVO(
                rec.getId(), "SYMPTOM", level, candidates, needMore,
                needMore ? followUpQuestions(ctx) : List.of(),
                needMore ? "信息还不够充分，补充上面的信息会更准确。" : null,
                needMore ? null : buildSuggestion(candidates.get(0)));
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

    /**
     * CNN 给不出结论时，让视觉大模型看一眼图，给一个「健康 / 有明显异常」的观察。
     * <p>
     * 为什么需要：自训模型只训练了 6 个物种的病害，对「健康绿萝」这类图会给出跨物种的
     * 错误结论（实测：绿萝照片被判成月季霜霉病 41%）。物种一致性过滤能挡住错误结论，
     * 但用户只看到"判断不了"同样没有价值。视觉大模型虽然不做精确病名，但判断
     * "有没有可见病斑"是可靠的，正好补上这一段。
     * <p>
     * 返回 null 表示大模型未配置或调用失败，调用方保持"判断不了"的诚实结论。
     */
    private String aiVisualObservation(byte[] imageBytes) {
        if (llm == null || !llm.isConfigured() || imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        String system = """
                你是植物病害初筛助手。只根据图片中**确实可见**的信息作答，禁止编造。
                严格只输出 JSON，不要 markdown 代码块：
                {"healthy": true/false, "observation": "你实际看到的症状，30 字内", "suggestion": "一句处置建议，30 字内"}
                规则：
                1. 没有可见的病斑、霉层、虫害、腐烂、明显萎蔫 → healthy=true。
                2. 有可见异常时 healthy=false，observation 写清颜色/位置/形态。
                3. 不要给具体病名（本地模型未收录时病名极易出错）。
                """;
        String user = "判断这张植物照片是否有可见的病害、虫害或腐烂迹象。只输出 JSON。";
        try {
            LlmClient.Result r = llm.chatVision(imageBytes, "image/jpeg", system, user);
            if (!r.ok()) {
                log.warn("视觉大模型初筛失败：{}", r.error());
                return null;
            }
            String txt = r.content() == null ? "" : r.content().trim();
            int s = txt.indexOf('{');
            int e = txt.lastIndexOf('}');
            if (s < 0 || e <= s) {
                return null;
            }
            JsonNode n = json.readTree(txt.substring(s, e + 1));
            boolean healthy = n.path("healthy").asBoolean(false);
            String obs = n.path("observation").asText("").trim();
            String sug = n.path("suggestion").asText("").trim();
            StringBuilder sb = new StringBuilder();
            sb.append(healthy ? "AI 观察：图片中未见明显病斑，植株看起来健康。" : "AI 观察：");
            if (!obs.isEmpty()) {
                sb.append(obs);
            }
            if (!sug.isEmpty()) {
                sb.append(" 建议：").append(sug);
            }
            return sb.toString();
        } catch (Exception ex) {
            log.warn("视觉大模型初筛异常：{}", ex.getMessage());
            return null;
        }
    }

    /**
     * 自训模型给不出结论时的兜底链路：
     * <ol>
     *   <li>视觉大模型先判断「看起来健康 / 有明显异常」（2–4 秒）</li>
     *   <li>有异常 → Pl@ntNet 病害识别拿候选（约 12 秒，覆盖物种有限）</li>
     *   <li>把候选交给大模型翻成中文、判断是否成立并给建议</li>
     *   <li>都不成立 → 退回视觉观察，如实说明</li>
     * </ol>
     * 任何一步失败都不抛异常，保证这一步始终能给出一个可读的答复。
     */
    private DiseaseDiagnosisVO noConclusionFallback(byte[] bytes, PlantPhotoEntity photo,
                                                    String speciesKey, String reason, Integer latency,
                                                    List<AiClientService.Item> cnnTop) {
        // ① 已知物种 → 闭集判断：只在该物种的病害清单里选，杜绝跨物种/编造病名
        //    实测有效：芦荟炭疽病照片，CNN 给「健康 36%」，闭集判断能选中「芦荟·炭疽病」
        if (speciesKey != null) {
            List<DiseaseInfoEntity> cands = diseaseInfoMapper.selectList(null).stream()
                    .filter(d -> d.getCode() != null && d.getCode().startsWith(speciesKey + "_"))
                    .toList();
            String text = aiClosedSetDisease(bytes, cands, cnnTop);
            if (text != null) {
                return verdict("VLM", text, latency);
            }
        }

        // ② 是否健康
        String health = aiVisualObservation(bytes);
        if (health != null && health.contains("健康")) {
            return verdict("VLM", health, latency);
        }

        // ③ 有异常 → 第三方病害识别（EPPO 候选 + 置信度）
        List<PlantNetDiseaseClient.Hit> hits = plantNetDisease.identify(bytes, photoService.filenameOf(photo));
        List<PlantNetDiseaseClient.Hit> strong = hits.stream()
                .filter(h -> h.score() >= 0.35)
                .toList();
        if (!strong.isEmpty()) {
            String text = aiInterpretDisease(bytes, strong, health);
            if (text != null) {
                return verdict("PLANTNET", text, latency);
            }
        }

        // ④ 都不成立 → 如实说明
        String shown = health != null ? health
                : "暂时无法识别这张照片（" + (reason == null ? "模型未就绪" : reason)
                  + "）。可以先到「AI 问答」里描述一下症状。";
        return verdict(health != null ? "VLM" : "NONE", shown, latency);
    }

    /**
     * 闭集病害判断：把该物种的病害名作为唯一候选，让视觉大模型选一个。
     * <p>
     * 为什么这样比"开放式给病名"可靠：
     * <ul>
     *   <li>候选来自本地知识库（29 条），模型无法编造不存在的病名</li>
     *   <li>范围限定在已识别物种内，不会出现「绿萝得月季霜霉病」</li>
     *   <li>选中后处置建议直接用知识库原文，不依赖模型自由发挥</li>
     * </ul>
     *
     * @return 中文结论；模型拿不准（pick=0）或未配置时返回 null，由调用方继续降级
     */
    private String aiClosedSetDisease(byte[] image, List<DiseaseInfoEntity> cands,
                                      List<AiClientService.Item> cnnTop) {
        if (cands.isEmpty() || llm == null || !llm.isConfigured()) {
            return null;
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < cands.size(); i++) {
            DiseaseInfoEntity d = cands.get(i);
            list.append(i + 1).append(". ").append(d.getNameCn());
            // 附上知识库里的典型症状：模型据此逐条比对（凹陷/轮纹/黄晕/孢子堆），
            // 实测把芦荟炭疽病的判断从"叶斑病"纠正为正确的"炭疽病"
            if (d.getSymptoms() != null && !d.getSymptoms().isBlank()) {
                list.append(" —— 典型症状：").append(abbrev(d.getSymptoms(), 90));
            }
            list.append('\n');
        }
        String system = """
                你是植物病害诊断助手。只能从给定的候选病名中选择，不能创造新病名。
                严格只输出 JSON，不要 markdown：
                {"pick": 编号（都无法确认时填 0）, "confidence": "高/中/低",
                 "evidence": ["图片中可见的依据1", "依据2"]}
                规则：
                1. 依据必须对应候选症状描述里的具体特征（有无凹陷、有无轮纹、有无黑点、有无黄晕、有无粉状孢子堆等）。
                2. 拿不准就填 0，不要硬选。
                """;
        // 把自训模型的倾向作为参考一并给出：它虽然置信度不足，但排序往往有信息量，
        // 大模型结合症状描述 + 这个先验做判断，比完全从零猜更稳。
        StringBuilder prior = new StringBuilder();
        if (cnnTop != null) {
            for (AiClientService.Item it : cnnTop) {
                String name = cands.stream()
                        .filter(d -> it.code() != null && it.code().equals(d.getCode()))
                        .map(DiseaseInfoEntity::getNameCn)
                        .findFirst()
                        .orElse(it.nameCn());
                prior.append(name).append(' ').append(Math.round(it.confidence() * 100)).append("%、");
            }
        }
        String user = "候选病名及典型症状：\n" + list
                + (prior.length() > 0 ? "\n自训模型倾向（仅供参考，可能判错）：" + prior + "\n" : "")
                + "\n请判断这张照片最符合哪一个。只输出 JSON。";
        try {
            LlmClient.Result r = llm.chatVision(image, "image/jpeg", system, user);
            if (!r.ok()) {
                log.warn("闭集病害判断失败：{}", r.error());
                return null;
            }
            String txt = r.content() == null ? "" : r.content().trim();
            int s = txt.indexOf('{');
            int e = txt.lastIndexOf('}');
            if (s < 0 || e <= s) {
                return null;
            }
            JsonNode n = json.readTree(txt.substring(s, e + 1));
            int pick = n.path("pick").asInt(0);
            if (pick < 1 || pick > cands.size()) {
                return null;
            }
            DiseaseInfoEntity d = cands.get(pick - 1);
            String conf = n.path("confidence").asText("").trim();
            List<String> ev = textList(n.path("evidence"));

            StringBuilder sb = new StringBuilder("可能：").append(d.getNameCn());
            // 若自训模型的前两名分差很小（本例锈病 31.5% vs 霜霉病 30.3%），
            // 说明图像上确实难以区分，硬给一个结论会误导。补一句「另一个可能 + 怎么鉴别」，
            // 让用户翻看叶背等特征自行确认——这比假装确定更有用。
            String[] tie = nearTieNote(cands, cnnTop, d);
            if (tie != null) {
                sb.append(" 或 ").append(tie[0]);
            }
            if (!conf.isEmpty()) {
                sb.append("（").append(conf).append("）");
            }
            if (tie != null && tie[1] != null && !tie[1].isEmpty()) {
                sb.append("。鉴别：").append(tie[1].replaceAll("[。；;\\s]+$", ""));
            }
            if (!ev.isEmpty()) {
                sb.append("。依据：").append(String.join("；", ev));
            }
            // 处置建议优先用知识库原文（人工整理、可靠），知识库没有才用模型的
            if (d.getTreatment() != null && !d.getTreatment().isBlank()) {
                sb.append("。建议：").append(String.join("；", splitTreatment(d.getTreatment())));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.warn("闭集病害判断异常：{}", ex.getMessage());
            return null;
        }
    }

    /**
     * 自训模型前两名分差很小时，返回「另一个可能 + 鉴别依据」的提示文本，否则返回 null。
     * <p>
     * 依据：实测月季霜霉病照片，CNN 给 锈病 31.5% / 霜霉病 30.3%，两者在图像上极难区分，
     * 硬给单一结论会误导。此时把接近的候选一并列出，并给出知识库里的鉴别特征
     * （锈病看叶背橙黄色粉末，霜霉病看灰紫色霉层），让用户自己确认。
     */
    private String[] nearTieNote(List<DiseaseInfoEntity> cands,
                                 List<AiClientService.Item> cnnTop,
                                 DiseaseInfoEntity picked) {
        if (cnnTop == null || cnnTop.size() < 2) {
            return null;
        }
        Map<String, Double> prob = new java.util.HashMap<>();
        for (AiClientService.Item it : cnnTop) {
            if (it.code() != null) {
                prob.put(it.code(), it.confidence());
            }
        }
        Double pickedProb = prob.get(picked.getCode());
        if (pickedProb == null) {
            return null;
        }
        for (DiseaseInfoEntity d : cands) {
            if (d.getCode().equals(picked.getCode())) {
                continue;
            }
            Double other = prob.get(d.getCode());
            if (other == null || Math.abs(other - pickedProb) > 0.08) {
                continue;
            }
            String sym = d.getSymptoms() == null ? "" : abbrev(d.getSymptoms(), 40);
            return new String[]{d.getNameCn(), sym};
        }
        return null;
    }

    /** 取前 n 个字符，超出加省略号（喂给大模型的症状描述不需要全文） */
    private static String abbrev(String text, int n) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }

    /** 把知识库里的多行/编号列表处置建议拆成短句 */
    private static List<String> splitTreatment(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String t = line.replaceAll("^\\s*\\d+[.、)]\\s*", "").trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
            if (out.size() >= 3) {
                break;
            }
        }
        return out;
    }

    /** 组装一个"无结构化预测、只有文字结论"的结果 */
    private DiseaseDiagnosisVO verdict(String provider, String text, Integer latency) {
        return new DiseaseDiagnosisVO(null, "DISEASE", provider, null, List.of(),
                null, null, null, text, false, text, latency, null);
    }

    /**
     * 让大模型结合图片判断 Pl@ntNet 的候选是否成立，并输出中文结论。
     * 返回 null 表示：大模型不可用，或它认为候选都不成立（此时调用方退回视觉观察）。
     */
    private String aiInterpretDisease(byte[] image, List<PlantNetDiseaseClient.Hit> hits, String observation) {
        if (llm == null || !llm.isConfigured()) {
            return null;
        }
        StringBuilder cand = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            PlantNetDiseaseClient.Hit h = hits.get(i);
            cand.append(i + 1).append(". ").append(h.label())
                    .append("（置信度 ").append(Math.round(h.score() * 100)).append("%）\n");
        }
        String system = """
                你是植物病害初筛助手。下面给出第三方病害识别服务（Pl@ntNet）的候选，以及初步观察。
                请用**中文**判断哪个候选最可能成立。
                严格只输出 JSON，不要 markdown：
                {"verdict": "中文常见名；候选都不成立时写 无法确认", "confidence": "高/中/低",
                 "evidence": ["图片中看到的依据1", "依据2"], "advice": ["处置建议1", "建议2"]}
                规则：
                1. 候选与图片明显不符时写「无法确认」，不要硬套。
                2. 依据必须是图片里能看到的东西（颜色、位置、形态）。
                3. 涉及用药只说"按产品标签稀释使用"，禁止编造浓度。
                """;
        String user = "Pl@ntNet 候选：\n" + cand
                + (observation == null ? "" : "\n初步观察：" + observation);
        try {
            LlmClient.Result r = llm.chatVision(image, "image/jpeg", system, user);
            if (!r.ok()) {
                log.warn("大模型解读病害候选失败：{}", r.error());
                return null;
            }
            String txt = r.content() == null ? "" : r.content().trim();
            int s = txt.indexOf('{');
            int e = txt.lastIndexOf('}');
            if (s < 0 || e <= s) {
                return null;
            }
            JsonNode n = json.readTree(txt.substring(s, e + 1));
            String name = n.path("verdict").asText("").trim();
            if (name.isEmpty() || name.contains("无法确认")) {
                return null;
            }
            StringBuilder sb = new StringBuilder("可能：").append(name);
            String conf = n.path("confidence").asText("").trim();
            if (!conf.isEmpty()) {
                sb.append("（").append(conf).append("）");
            }
            List<String> ev = textList(n.path("evidence"));
            if (!ev.isEmpty()) {
                sb.append("。依据：").append(String.join("；", ev));
            }
            List<String> ad = textList(n.path("advice"));
            if (!ad.isEmpty()) {
                sb.append("。建议：").append(String.join("；", ad));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.warn("大模型解读病害候选异常：{}", ex.getMessage());
            return null;
        }
    }

    private static List<String> textList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode x : arr) {
                String t = x.asText("");
                if (!t.isBlank()) {
                    out.add(t.trim());
                }
            }
        }
        return out;
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

    /**
     * 该植株对应的模型物种 key；查不到返回 null，调用方回退通用模型。
     * <p>
     * 这一步决定是否使用「物种专用模型」：AI 服务收到 species=Aloe 会路由到
     * ai-service/models/specialized/aloe/，否则一律走通用 29 类模型。
     */
    private String resolveSpeciesKey(Long userPlantId) {
        if (userPlantId == null) {
            return null;
        }
        UserPlantEntity plant = userPlantMapper.selectById(userPlantId);
        if (plant == null || plant.getSpeciesId() == null) {
            return null;
        }
        PlantSpeciesEntity species = plantSpeciesMapper.selectById(plant.getSpeciesId());
        return species == null ? null : SpeciesKeys.keyOf(species.getCommonName());
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

    /**
     * 把 action_plan 的多行文本拆成前端可渲染的步骤数组。
     * <p>
     * 种子数据形如 "1. 立即停止浇水\n2. 检查排水孔…"；
     * 前端 DiagnosisEvidence.vue 用 &lt;ol&gt; 渲染 actionPlan 数组，
     * 若直接给字符串，v-for 会逐字符迭代，页面会变成一堆单字。
     */
    private static List<String> splitActionPlan(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> steps = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            // 去掉行首编号：1. / 1、 / 1) / - / •
            line = line.replaceFirst("^\\s*(?:\\d+[.、)）]|[-•*])\\s*", "").trim();
            if (!line.isEmpty()) {
                steps.add(line);
            }
        }
        return steps.isEmpty() ? List.of(text.trim()) : steps;
    }

    /** 综合建议：一句话说清结论并给出下一步动作（前端「综合建议」区块） */
    private static String buildSuggestion(SymptomDiagnosisVO.Candidate top) {
        StringBuilder sb = new StringBuilder("综合来看，最可能是「").append(top.nameCn()).append("」");
        if (top.confidence() != null) {
            sb.append("（置信度 ").append(Math.round(top.confidence() * 100)).append("%）");
        }
        sb.append("。建议按上面的步骤处理；若 3–5 天没有改善，可以复拍一张照片复诊，"
                + "或到「AI 问答」补充更多细节。");
        return sb.toString();
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
