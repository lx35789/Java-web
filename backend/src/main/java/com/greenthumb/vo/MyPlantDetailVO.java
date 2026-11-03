package com.greenthumb.vo;

import java.util.List;

/**
 * 植物详情。
 * 结构与 frontend/src/mock/index.js 的 GET /my-plants/:id 一致：
 * 植物字段平铺在顶层，另加 carePlans / recentLogs / recentDiagnoses 三个数组。
 * <p>
 * 现在后三个数组都返回空数组 —— 养护计划、养护日志、诊断记录还没实现。
 * 前端有兜底（空列表显示"暂无"），所以不会报错。
 */
public record MyPlantDetailVO(
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
        String lastWateredAt,
        String nextWaterDueDate,
        List<Object> carePlans,
        List<Object> recentLogs,
        List<Object> recentDiagnoses
) {

    public static MyPlantDetailVO from(MyPlantVO v) {
        return new MyPlantDetailVO(
                v.id(), v.speciesId(), v.speciesName(), v.speciesEmoji(),
                v.nickname(), v.location(), v.lightLevel(),
                v.potSize(), v.potMaterial(), v.soilType(),
                v.acquiredDate(), v.healthStatus(), v.addSource(), v.note(),
                v.createdAt(), v.lastWateredAt(), v.nextWaterDueDate(),
                List.of(), List.of(), List.of());
    }
}
