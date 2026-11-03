package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.common.PageResult;
import com.greenthumb.entity.PlantSpeciesEntity;
import com.greenthumb.mapper.PlantSpeciesMapper;
import com.greenthumb.vo.SpeciesVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 植物种类库。
 */
@Service
public class SpeciesService {

    private final PlantSpeciesMapper plantSpeciesMapper;
    private final ObjectMapper objectMapper;

    public SpeciesService(PlantSpeciesMapper plantSpeciesMapper, ObjectMapper objectMapper) {
        this.plantSpeciesMapper = plantSpeciesMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 列表查询（分页）。
     * 筛选条件与 frontend/src/mock/index.js 的 GET /species 完全一致：
     * keyword 命中中文名 / 英文名 / 学名 / 别名，另支持 light、difficulty、toxic。
     */
    public PageResult<SpeciesVO> list(String keyword, String light, String difficulty,
                                      String toxic, long page, long size) {
        LambdaQueryWrapper<PlantSpeciesEntity> w = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            w.and(q -> q.like(PlantSpeciesEntity::getCommonName, kw)
                    .or().like(PlantSpeciesEntity::getCommonNameEn, kw)
                    .or().like(PlantSpeciesEntity::getScientificName, kw)
                    .or().like(PlantSpeciesEntity::getAliases, kw));
        }
        if (light != null && !light.isBlank()) {
            w.eq(PlantSpeciesEntity::getLightRequirement, light.trim());
        }
        if (difficulty != null && !difficulty.isBlank()) {
            w.eq(PlantSpeciesEntity::getDifficulty, difficulty.trim());
        }
        if ("true".equalsIgnoreCase(toxic)) {
            w.eq(PlantSpeciesEntity::getToxicity, "TOXIC");
        } else if ("false".equalsIgnoreCase(toxic)) {
            w.eq(PlantSpeciesEntity::getToxicity, "NONE");
        }
        w.orderByAsc(PlantSpeciesEntity::getId);

        Page<PlantSpeciesEntity> result = plantSpeciesMapper.selectPage(new Page<>(page, size), w);
        List<SpeciesVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, result.getTotal(), page, size);
    }

    public SpeciesVO detail(Long id) {
        PlantSpeciesEntity entity = plantSpeciesMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "植物种类不存在");
        }
        return toVO(entity);
    }

    /* ================= 内部 ================= */

    public SpeciesVO toVO(PlantSpeciesEntity e) {
        return new SpeciesVO(
                e.getId(),
                e.getEmoji(),
                e.getCommonName(),
                e.getCommonNameEn(),
                e.getScientificName(),
                parseList(e.getAliases(), new TypeReference<List<String>>() {
                }),
                e.getFamily(),
                e.getDifficulty(),
                e.getLightRequirement(),
                e.getHumidityRequirement(),
                e.getBaseWaterIntervalDays(),
                e.getWaterAmountMl(),
                e.getFertilizerIntervalDays(),
                e.getFertilizerType(),
                e.getSoilType(),
                e.getGrowthRate(),
                e.getMaxHeightCm(),
                e.getTempMinC(),
                e.getTempMaxC(),
                e.getToxicity(),
                parseList(e.getToxicTo(), new TypeReference<List<Map<String, Object>>>() {
                }),
                e.getModelClassIndex(),
                e.getRecognitionProvider(),
                e.getDescription(),
                parseList(e.getCareTips(), new TypeReference<List<String>>() {
                }),
                parseMap(e.getSeasonCoefficients())
        );
    }

    /** JSON 数组列 → List；解析失败或为空返回空列表，绝不让脏数据把接口打挂 */
    private <T> List<T> parseList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<T> v = objectMapper.readValue(json, type);
            return v == null ? Collections.emptyList() : v;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> v = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return v == null ? Collections.emptyMap() : v;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }
}
