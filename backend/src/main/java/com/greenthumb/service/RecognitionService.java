package com.greenthumb.service;

import com.greenthumb.entity.PlantPhotoEntity;
import com.greenthumb.entity.PlantSpeciesEntity;
import com.greenthumb.entity.RecognitionRecordEntity;
import com.greenthumb.entity.RecognitionResultEntity;
import com.greenthumb.mapper.PlantSpeciesMapper;
import com.greenthumb.mapper.RecognitionRecordMapper;
import com.greenthumb.mapper.RecognitionResultMapper;
import com.greenthumb.recognition.RecognitionCandidate;
import com.greenthumb.recognition.RecognitionOrchestrator;
import com.greenthumb.vo.RecognitionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    public RecognitionService(PhotoService photoService,
                              RecognitionOrchestrator orchestrator,
                              RecognitionRecordMapper recordMapper,
                              RecognitionResultMapper resultMapper,
                              PlantSpeciesMapper plantSpeciesMapper) {
        this.photoService = photoService;
        this.orchestrator = orchestrator;
        this.recordMapper = recordMapper;
        this.resultMapper = resultMapper;
        this.plantSpeciesMapper = plantSpeciesMapper;
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
        for (RecognitionCandidate c : outcome.candidates()) {
            if (items.size() >= MAX_RESULTS) {
                break;
            }
            PlantSpeciesEntity sp = match(c, allSpecies);
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
