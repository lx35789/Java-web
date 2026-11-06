package com.greenthumb.service;

import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.dto.ConfirmRecognitionRequest;
import com.greenthumb.dto.CreatePlantRequest;
import com.greenthumb.entity.PlantPhotoEntity;
import com.greenthumb.entity.PlantSpeciesEntity;
import com.greenthumb.entity.RecognitionRecordEntity;
import com.greenthumb.entity.RecognitionResultEntity;
import com.greenthumb.mapper.PlantSpeciesMapper;
import com.greenthumb.mapper.RecognitionRecordMapper;
import com.greenthumb.mapper.RecognitionResultMapper;
import com.greenthumb.recognition.RecognitionCandidate;
import com.greenthumb.recognition.RecognitionOrchestrator;
import com.greenthumb.vo.ConfirmRecognitionVO;
import com.greenthumb.vo.RecognitionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 物种识别。
 * <p>
 * 流程：取照片 → 读字节 → 交给降级链 → 把第三方返回的名字映射到植物库 → 落库 → 返回
 * <p>
 * 第三方返回的名称**不一定**在我们的库里（百度能认 2 万多种，我们只有 10 种），
 * 所以 recognition_result.species_id 允许为空，同时保留 raw_name。
 * 这样以后统计"模型能认但库里没有"的物种时，数据是现成的。
 */
@Service
public class RecognitionService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionService.class);
    private static final int MAX_RESULTS = 3;
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.5;

    private final PhotoService photoService;
    private final RecognitionOrchestrator orchestrator;
    private final RecognitionRecordMapper recordMapper;
    private final RecognitionResultMapper resultMapper;
    private final PlantSpeciesMapper plantSpeciesMapper;
    private final MyPlantService myPlantService;

    public RecognitionService(PhotoService photoService,
                              RecognitionOrchestrator orchestrator,
                              RecognitionRecordMapper recordMapper,
                              RecognitionResultMapper resultMapper,
                              PlantSpeciesMapper plantSpeciesMapper,
                              MyPlantService myPlantService) {
        this.photoService = photoService;
        this.orchestrator = orchestrator;
        this.recordMapper = recordMapper;
        this.resultMapper = resultMapper;
        this.plantSpeciesMapper = plantSpeciesMapper;
        this.myPlantService = myPlantService;
    }

    @Transactional
    public RecognitionVO recognize(Long userId, Long photoId) {
        PlantPhotoEntity photo = photoService.getOwned(userId, photoId);
        byte[] bytes = photoService.readBytes(photo);

        RecognitionOrchestrator.Outcome outcome =
                orchestrator.recognize(bytes, photoService.filenameOf(photo));

        List<PlantSpeciesEntity> allSpecies = plantSpeciesMapper.selectList(null);

        List<RecognitionVO.Item> items = new ArrayList<>();
        int rank = 1;
        // 去重：百度经常把同一株植物返回两条（不同百科入口），
        // 之前会让前端出现两个一模一样的「月季」候选，用户无法区分。
        Set<String> seen = new HashSet<>();
        for (RecognitionCandidate c : outcome.candidates()) {
            if (items.size() >= MAX_RESULTS) {
                break;
            }
            PlantSpeciesEntity sp = match(c, allSpecies);
            String key = sp != null
                    ? "id:" + sp.getId()
                    : "name:" + (c.rawName() == null ? "" : c.rawName().trim());
            if (!seen.add(key)) {
                continue;
            }
            items.add(new RecognitionVO.Item(
                    rank++,
                    sp == null ? null : sp.getId(),
                    sp == null ? c.rawName() : sp.getCommonName(),
                    sp == null ? c.scientificName() : sp.getScientificName(),
                    sp == null ? "🌿" : sp.getEmoji(),
                    round(c.confidence()),
                    sp == null ? null : summary(sp)));
        }

        // 落库
        RecognitionRecordEntity rec = new RecognitionRecordEntity();
        rec.setUserId(userId);
        rec.setPhotoId(photo.getId());
        rec.setProvider(outcome.provider());
        rec.setModelVersion(null);
        rec.setLatencyMs(outcome.latencyMs());
        rec.setConfirmed(0);
        rec.setCreatedAt(LocalDateTime.now());
        if (!items.isEmpty()) {
            rec.setTop1SpeciesId(items.get(0).speciesId());
            rec.setTop1Confidence(BigDecimal.valueOf(items.get(0).confidence()));
        }
        recordMapper.insert(rec);

        for (RecognitionVO.Item it : items) {
            RecognitionResultEntity r = new RecognitionResultEntity();
            r.setRecordId(rec.getId());
            r.setRankNo(it.rank());
            r.setSpeciesId(it.speciesId());
            r.setRawName(it.commonName());
            r.setConfidence(BigDecimal.valueOf(it.confidence()));
            resultMapper.insert(r);
        }

        boolean low = items.isEmpty()
                || items.get(0).confidence() < LOW_CONFIDENCE_THRESHOLD;

        return new RecognitionVO(rec.getId(), outcome.provider(), null, outcome.latencyMs(),
                low, outcome.fallbackUsed(), items);
    }

    /** 读一条识别记录（含 Top-3） */
    public RecognitionVO get(Long userId, Long recordId) {
        RecognitionRecordEntity rec = recordMapper.selectById(recordId);
        if (rec == null || !rec.getUserId().equals(userId)) {
            throw new com.greenthumb.common.BizException(
                    com.greenthumb.common.ErrorCode.NOT_FOUND, "识别记录不存在");
        }
        List<RecognitionResultEntity> rows = resultMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionResultEntity>()
                        .eq(RecognitionResultEntity::getRecordId, recordId)
                        .orderByAsc(RecognitionResultEntity::getRankNo));

        List<RecognitionVO.Item> items = new ArrayList<>();
        for (RecognitionResultEntity r : rows) {
            PlantSpeciesEntity sp = r.getSpeciesId() == null ? null : plantSpeciesMapper.selectById(r.getSpeciesId());
            items.add(new RecognitionVO.Item(
                    r.getRankNo() == null ? items.size() + 1 : r.getRankNo(),
                    r.getSpeciesId(),
                    sp == null ? r.getRawName() : sp.getCommonName(),
                    sp == null ? null : sp.getScientificName(),
                    sp == null ? "🌿" : sp.getEmoji(),
                    r.getConfidence() == null ? 0d : r.getConfidence().doubleValue(),
                    sp == null ? null : summary(sp)));
        }
        boolean low = items.isEmpty() || items.get(0).confidence() < LOW_CONFIDENCE_THRESHOLD;
        return new RecognitionVO(rec.getId(), rec.getProvider(), rec.getModelVersion(),
                rec.getLatencyMs(), low, false, items);
    }

    /**
     * 确认识别结果（用户选定最终物种），可选同时创建一盆植物。
     *
     * 前端的「加入我的植物」按钮就调这个。之前接口不存在，点下去报
     * 「接口不存在」，但 Mock 里有实现（mock/index.js 的
     * route('POST', '/recognitions/:id/confirm')），所以浏览器里看不出问题，
     * 只有真机走真实接口才暴露。
     *
     * @param userId  当前登录用户（用于校验记录归属，防越权）
     * @param recordId 识别记录 id
     * @param req     确认请求；createPlant=true 时创建植物
     */
    @Transactional
    public ConfirmRecognitionVO confirm(Long userId, Long recordId, ConfirmRecognitionRequest req) {
        RecognitionRecordEntity rec = recordMapper.selectById(recordId);
        if (rec == null || !Objects.equals(rec.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "识别记录不存在");
        }
        if (req == null || req.speciesId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要确认的物种");
        }
        if (plantSpeciesMapper.selectById(req.speciesId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "植物种类不存在");
        }

        Long userPlantId = null;
        if (Boolean.TRUE.equals(req.createPlant())) {
            ConfirmRecognitionRequest.UserPlantForm f = req.userPlantForm();
            CreatePlantRequest cp = new CreatePlantRequest(
                    req.speciesId(),
                    f == null ? null : f.nickname(),
                    f == null ? null : f.location(),
                    f == null ? null : f.lightLevel(),
                    f == null ? null : f.potSize(),
                    f == null ? null : f.potMaterial(),
                    f == null ? null : f.soilType(),
                    f == null ? null : f.acquiredDate(),
                    f == null ? null : f.note());
            userPlantId = myPlantService.create(userId, cp).id();
        }

        // 回写确认状态。同时把 top1 改成用户选的物种：
        // 用户纠正模型也是「训练/统计」的宝藏数据，不能只记一个 confirmed 标志。
        RecognitionRecordEntity patch = new RecognitionRecordEntity();
        patch.setId(rec.getId());
        patch.setConfirmed(1);
        patch.setTop1SpeciesId(req.speciesId());
        if (userPlantId != null) {
            patch.setUserPlantId(userPlantId);
        }
        recordMapper.updateById(patch);

        log.info("确认识别记录 {}：speciesId={} userPlantId={}", recordId, req.speciesId(), userPlantId);
        // 养护计划模块尚未实现，如实返回 false，不谎报成功
        return new ConfirmRecognitionVO(userPlantId, false, req.speciesId());
    }

    /* ================= 内部 ================= */
    /**
     * 把第三方返回的名字映射到植物库。
     * 匹配顺序：中文名精确 → 学名精确 → 别名包含。
     * 匹配不上返回 null（很正常，百度能认 2 万种，我们只有 10 种）。
     */
    private PlantSpeciesEntity match(RecognitionCandidate c, List<PlantSpeciesEntity> all) {
        String raw = c.rawName() == null ? "" : c.rawName().trim();
        String sci = c.scientificName() == null ? "" : c.scientificName().trim();

        for (PlantSpeciesEntity s : all) {
            if (!raw.isEmpty() && raw.equals(s.getCommonName())) {
                return s;
            }
        }
        for (PlantSpeciesEntity s : all) {
            if (!sci.isEmpty() && s.getScientificName() != null && sci.equalsIgnoreCase(s.getScientificName())) {
                return s;
            }
        }
        for (PlantSpeciesEntity s : all) {
            if (!raw.isEmpty() && s.getAliases() != null && s.getAliases().contains(raw)) {
                return s;
            }
        }
        return null;
    }

    /** 列表里给的一句养护摘要，取自物种 description */
    private static String summary(PlantSpeciesEntity sp) {
        String d = sp.getDescription();
        if (d == null || d.isBlank()) {
            return "基准浇水间隔 " + sp.getBaseWaterIntervalDays() + " 天";
        }
        return d.length() > 42 ? d.substring(0, 42) + "…" : d;
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
