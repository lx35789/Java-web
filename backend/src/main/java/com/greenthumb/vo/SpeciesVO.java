package com.greenthumb.vo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 植物种类。
 * 字段与 frontend/src/mock/data.js 的 mockSpecies 保持一致。
 * JSON 列已经解析成前端要的数组/对象。
 */
public record SpeciesVO(
        Long id,
        String emoji,
        String commonName,
        String commonNameEn,
        String scientificName,
        List<String> aliases,
        String family,
        String difficulty,
        String lightRequirement,
        String humidityRequirement,
        Integer baseWaterIntervalDays,
        Integer waterAmountMl,
        Integer fertilizerIntervalDays,
        String fertilizerType,
        String soilType,
        String growthRate,
        Integer maxHeightCm,
        BigDecimal tempMinC,
        BigDecimal tempMaxC,
        String toxicity,
        List<Map<String, Object>> toxicTo,
        Integer modelClassIndex,
        String recognitionProvider,
        String description,
        List<String> careTips,
        Map<String, Object> seasonCoefficients
) {
}
