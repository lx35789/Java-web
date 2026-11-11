package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.dto.CreatePlantRequest;
import com.greenthumb.entity.CareLogEntity;
import com.greenthumb.entity.CarePlanEntity;
import com.greenthumb.entity.CareTaskEntity;
import com.greenthumb.entity.DiseaseRecordEntity;
import com.greenthumb.entity.PlantPhotoEntity;
import com.greenthumb.entity.PlantSpeciesEntity;
import com.greenthumb.entity.UserPlantEntity;
import com.greenthumb.mapper.CareLogMapper;
import com.greenthumb.mapper.CarePlanMapper;
import com.greenthumb.mapper.CareTaskMapper;
import com.greenthumb.mapper.DiseaseRecordMapper;
import com.greenthumb.mapper.PlantPhotoMapper;
import com.greenthumb.mapper.PlantSpeciesMapper;
import com.greenthumb.mapper.UserPlantMapper;
import com.greenthumb.vo.MyPlantDetailVO;
import com.greenthumb.vo.MyPlantVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 我的植物。
 * <p>
 * 查询策略：先按 user_id 查 user_plant，再按 speciesId 集合批量查 plant_species，
 * 在内存里拼装。避免 N+1 查询，也不需要写自定义 SQL。
 * <p>
 * ★ 数据隔离：所有方法第一件事就是用 user_id 过滤或校验归属。
 *   这是所有业务接口的模板 —— 绝不能允许 A 用户读到/改到 B 用户的数据。
 */
@Service
public class MyPlantService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserPlantMapper userPlantMapper;
    private final PlantSpeciesMapper plantSpeciesMapper;
    private final CarePlanMapper carePlanMapper;
    private final CareLogMapper careLogMapper;
    private final CareTaskMapper careTaskMapper;
    private final PlantPhotoMapper plantPhotoMapper;
    private final DiseaseRecordMapper diseaseRecordMapper;

    public MyPlantService(UserPlantMapper userPlantMapper,
                          PlantSpeciesMapper plantSpeciesMapper,
                          CarePlanMapper carePlanMapper,
                          CareLogMapper careLogMapper,
                          CareTaskMapper careTaskMapper,
                          PlantPhotoMapper plantPhotoMapper,
                          DiseaseRecordMapper diseaseRecordMapper) {
        this.userPlantMapper = userPlantMapper;
        this.plantSpeciesMapper = plantSpeciesMapper;
        this.carePlanMapper = carePlanMapper;
        this.careLogMapper = careLogMapper;
        this.careTaskMapper = careTaskMapper;
        this.plantPhotoMapper = plantPhotoMapper;
        this.diseaseRecordMapper = diseaseRecordMapper;
    }

    /* ================= 列表 ================= */

    public List<MyPlantVO> list(Long userId, String healthStatus) {
        LambdaQueryWrapper<UserPlantEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPlantEntity::getUserId, userId);
        if (healthStatus != null && !healthStatus.isBlank()) {
            wrapper.eq(UserPlantEntity::getHealthStatus, healthStatus);
        }
        wrapper.orderByDesc(UserPlantEntity::getCreatedAt);

        List<UserPlantEntity> plants = userPlantMapper.selectList(wrapper);
        if (plants.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, PlantSpeciesEntity> speciesMap = loadSpecies(plants);
        return plants.stream().map(p -> toVO(p, speciesMap.get(p.getSpeciesId()))).toList();
    }

    /* ================= 详情 ================= */

    /**
     * 详情：基本信息 + 养护计划 + 最近养护记录 + 最近诊断。
     *
     * <p>前端 plants/detail.vue 直接渲染 carePlans / recentLogs / recentDiagnoses，
     * 早期实现这三项固定传空列表，导致详情页永远显示「还没有」「没有任何记录」。
     */
    public MyPlantDetailVO detail(Long userId, Long plantId) {
        UserPlantEntity plant = getOwned(userId, plantId);
        PlantSpeciesEntity species = speciesOf(plant.getSpeciesId());
        MyPlantVO base = toVO(plant, species);

        // 计划：详情页要展示「为什么是 N 天」，所以必须给全字段（含系数明细），
        // 不能只给 id/planType/间隔 —— 否则 CarePlanExplain 渲染成「— 天」。
        List<Object> plans = carePlanMapper.selectList(new LambdaQueryWrapper<CarePlanEntity>()
                        .eq(CarePlanEntity::getUserId, userId)
                        .eq(CarePlanEntity::getUserPlantId, plantId)
                        .eq(CarePlanEntity::getIsActive, 1)
                        .orderByAsc(CarePlanEntity::getPlanType))
                .stream().map(p -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("userPlantId", p.getUserPlantId());
                    m.put("planType", p.getPlanType());
                    m.put("season", p.getSeason());
                    m.put("baseIntervalDays", p.getBaseIntervalDays());
                    m.put("effectiveIntervalDays", p.getEffectiveIntervalDays());
                    m.put("coefficientDetail", parseJsonOrNull(p.getCoefficientDetail()));
                    m.put("llmExplain", p.getLlmExplain());
                    m.put("isActive", p.getIsActive() != null && p.getIsActive() == 1);
                    m.put("generatedBy", p.getGeneratedBy());
                    m.put("nextDueDate", nextDueOf(userId, p.getId()));
                    return (Object) m;
                })
                .toList();

        // 最近 5 条养护记录
        List<Object> logs = careLogMapper.selectList(new LambdaQueryWrapper<CareLogEntity>()
                        .eq(CareLogEntity::getUserId, userId)
                        .eq(CareLogEntity::getUserPlantId, plantId)
                        .orderByDesc(CareLogEntity::getLoggedAt)
                        .last("limit 5"))
                .stream().map(l -> (Object) java.util.Map.of(
                        "id", l.getId(),
                        "logType", l.getLogType(),
                        "amount", l.getAmount() == null ? "" : l.getAmount(),
                        "unit", l.getUnit() == null ? "" : l.getUnit(),
                        "detail", l.getDetail() == null ? "" : l.getDetail(),
                        "loggedAt", l.getLoggedAt() == null ? "" : l.getLoggedAt().format(DATE_TIME)))
                .toList();

        // 最近 5 条诊断
        List<Object> diagnoses = diseaseRecordMapper.selectList(
                        new LambdaQueryWrapper<DiseaseRecordEntity>()
                                .eq(DiseaseRecordEntity::getUserId, userId)
                                .orderByDesc(DiseaseRecordEntity::getCreatedAt)
                                .last("limit 5"))
                .stream().map(d -> (Object) java.util.Map.of(
                        "id", d.getId(),
                        "diagnosisType", d.getDiagnosisType(),
                        "predictedCode", d.getPredictedCode() == null ? "" : d.getPredictedCode(),
                        "confidence", d.getConfidence() == null ? 0 : d.getConfidence(),
                        "status", d.getStatus() == null ? "" : d.getStatus(),
                        "createdAt", d.getCreatedAt() == null ? "" : d.getCreatedAt().format(DATE_TIME)))
                .toList();

        return new MyPlantDetailVO(
                base.id(), base.speciesId(), base.speciesName(), base.speciesEmoji(),
                base.nickname(), base.location(), base.lightLevel(),
                base.potSize(), base.potMaterial(), base.soilType(),
                base.acquiredDate(), base.healthStatus(), base.addSource(), base.note(),
                base.createdAt(), lastWateredAt(userId, plantId), nextWaterDue(userId, plantId),
                plans, logs, diagnoses);
    }

    /** 解析系数 JSON 字符串为对象，让前端拿到的是对象而不是字符串 */
    private Object parseJsonOrNull(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 某计划下一次到期日（最早一条 PENDING 任务） */
    private String nextDueOf(Long userId, Long planId) {
        CareTaskEntity t = careTaskMapper.selectOne(new LambdaQueryWrapper<CareTaskEntity>()
                .eq(CareTaskEntity::getPlanId, planId)
                .eq(CareTaskEntity::getStatus, "PENDING")
                .orderByAsc(CareTaskEntity::getDueDate)
                .last("limit 1"));
        return t == null || t.getDueDate() == null ? null : t.getDueDate().format(DATE);
    }

    /** 最近一次浇水时间（从养护记录推，不冗余存字段） */
    private String lastWateredAt(Long userId, Long plantId) {
        CareLogEntity l = careLogMapper.selectOne(new LambdaQueryWrapper<CareLogEntity>()
                .eq(CareLogEntity::getUserId, userId)
                .eq(CareLogEntity::getUserPlantId, plantId)
                .eq(CareLogEntity::getLogType, "WATER")
                .orderByDesc(CareLogEntity::getLoggedAt)
                .last("limit 1"));
        return l == null || l.getLoggedAt() == null ? null : l.getLoggedAt().format(DATE_TIME);
    }

    /** 下一次浇水到期日（取最早一条未完成 WATER 任务） */
    private String nextWaterDue(Long userId, Long plantId) {
        CareTaskEntity t = careTaskMapper.selectOne(new LambdaQueryWrapper<CareTaskEntity>()
                .eq(CareTaskEntity::getUserId, userId)
                .eq(CareTaskEntity::getUserPlantId, plantId)
                .eq(CareTaskEntity::getTaskType, "WATER")
                .eq(CareTaskEntity::getStatus, "PENDING")
                .orderByAsc(CareTaskEntity::getDueDate)
                .last("limit 1"));
        return t == null || t.getDueDate() == null ? null : t.getDueDate().format(DATE);
    }

    /**
     * 成长时间线：把「拍过的照片」按时间倒序返回。
     *
     * <p>结构对齐 frontend（mock 版）：{ userPlantId, photos: [{id, date, emoji, note}] }。
     * 前端只读 photos，别的字段不要乱加。
     *
     * <p>数据来源是 plant_photo（拍照记录），不是 plant_observation ——
     * 后者是叶面积/健康分的计算产物，目前还没有稳定写入，拿它做时间线会空。
     */
    public java.util.Map<String, Object> timeline(Long userId, Long plantId) {
        getOwned(userId, plantId);   // 校验归属

        List<PlantPhotoEntity> photos = plantPhotoMapper.selectList(
                new LambdaQueryWrapper<PlantPhotoEntity>()
                        .eq(PlantPhotoEntity::getUserPlantId, plantId)
                        .orderByDesc(PlantPhotoEntity::getCreatedAt)
                        .last("limit 50"));

        List<java.util.Map<String, Object>> items = photos.stream().map(p -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("date", p.getCreatedAt() == null ? "" : p.getCreatedAt().format(DATE));
            m.put("emoji", "\uD83D\uDCF7");
            m.put("note", p.getPhotoType() == null ? "拍照记录" : "拍照记录 · " + p.getPhotoType());
            m.put("url", p.getUrl());
            return m;
        }).toList();

        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("userPlantId", plantId);
        out.put("photos", items);
        return out;
    }

    /* ================= 新增 ================= */

    @Transactional
    public MyPlantVO create(Long userId, CreatePlantRequest req) {
        if (req == null || req.speciesId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择植物种类");
        }
        PlantSpeciesEntity species = plantSpeciesMapper.selectById(req.speciesId());
        if (species == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "植物种类不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        UserPlantEntity p = new UserPlantEntity();
        p.setUserId(userId);
        p.setSpeciesId(req.speciesId());
        p.setNickname(blankToNull(req.nickname()) != null
                ? req.nickname().trim()
                : species.getCommonName());   // 没填昵称就用物种名，避免列表里显示空白
        p.setLocation(blankToNull(req.location()));
        p.setLightLevel(blankToNull(req.lightLevel()));
        p.setPotSize(blankToNull(req.potSize()));
        p.setPotMaterial(blankToNull(req.potMaterial()));
        // 用户没填土壤就取物种的推荐值
        p.setSoilType(blankToNull(req.soilType()) != null ? req.soilType().trim() : species.getSoilType());
        p.setAcquiredDate(parseDate(req.acquiredDate()));
        p.setHealthStatus("HEALTHY");
        p.setAddSource("MANUAL");
        p.setNote(blankToNull(req.note()));
        p.setDeleted(0);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        userPlantMapper.insert(p);

        return toVO(p, species);
    }

    /* ================= 内部 ================= */

    /** 取一盆植物并校验归属，防止越权访问别人的植物 */
    public UserPlantEntity getOwned(Long userId, Long plantId) {
        if (plantId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少植物 id");
        }
        UserPlantEntity plant = userPlantMapper.selectById(plantId);
        if (plant == null || !Objects.equals(plant.getUserId(), userId)) {
            // 故意返回「不存在」而不是「无权限」，避免暴露别人植物的 id 是否存在
            throw new BizException(ErrorCode.NOT_FOUND, "植物不存在");
        }
        return plant;
    }

    public PlantSpeciesEntity speciesOf(Long speciesId) {
        return speciesId == null ? null : plantSpeciesMapper.selectById(speciesId);
    }

    private Map<Long, PlantSpeciesEntity> loadSpecies(List<UserPlantEntity> plants) {
        Set<Long> ids = plants.stream()
                .map(UserPlantEntity::getSpeciesId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return plantSpeciesMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PlantSpeciesEntity::getId, Function.identity(), (a, b) -> a));
    }

    public MyPlantVO toVO(UserPlantEntity p, PlantSpeciesEntity s) {
        return new MyPlantVO(
                p.getId(),
                p.getSpeciesId(),
                s == null ? null : s.getCommonName(),
                s == null ? null : s.getEmoji(),
                p.getNickname(),
                p.getLocation(),
                p.getLightLevel(),
                p.getPotSize(),
                p.getPotMaterial(),
                p.getSoilType(),
                formatDate(p.getAcquiredDate()),
                p.getHealthStatus(),
                p.getAddSource(),
                p.getNote(),
                formatDateTime(p.getCreatedAt()),
                null,   // lastWateredAt   —— 养护日志功能完成后填充
                null    // nextWaterDueDate —— 养护计划功能完成后填充
        );
    }

    private static String formatDate(LocalDate d) {
        return d == null ? null : d.format(DATE);
    }

    private static String formatDateTime(LocalDateTime t) {
        return t == null ? null : t.format(ISO);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
