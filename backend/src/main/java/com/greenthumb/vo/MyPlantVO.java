package com.greenthumb.vo;

/**
 * 「我的植物」列表项。
 * 字段与 frontend/src/mock/data.js 的 mockPlants 保持一致，页面代码零改动。
 */
public record MyPlantVO(
        Long id,
        Long speciesId,
        String speciesName,
        String speciesEmoji,
        String nickname,
        String location,
        String lightLevel,
        String potSize,
        String potMaterial,
        String soilType,
        String acquiredDate,
        String healthStatus,
        String addSource,
        String note,
        String createdAt,
        /** 最近一次浇水时间；养护计划功能做好前为 null */
        String lastWateredAt,
        /** 下次浇水到期日；养护计划功能做好前为 null，前端会显示「尚未生成养护计划」 */
        String nextWaterDueDate
) {
}
