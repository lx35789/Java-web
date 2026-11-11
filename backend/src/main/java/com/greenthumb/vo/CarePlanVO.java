package com.greenthumb.vo;

/**
 * 养护计划视图。字段名与 frontend/src/mock/data.js 的 mockCarePlans 对齐，
 * 前端从 Mock 切到真实接口零改动。
 *
 * @param coefficientDetail JSON 字符串，形如 {"season":0.8,"light":1.15,"pot":0.9}
 *                          前端可渲染"基准 7 天 × 夏季 0.8 × ... = 5 天"
 * @param nextDueDate       下一次到期日 yyyy-MM-dd，取自最早一条 PENDING 任务
 */
public record CarePlanVO(
        Long id,
        Long userPlantId,
        String nickname,
        String speciesName,
        String speciesEmoji,
        String location,
        String planType,
        String season,
        Integer baseIntervalDays,
        Integer effectiveIntervalDays,
        String coefficientDetail,
        String llmExplain,
        String nextDueDate,
        Boolean isActive,
        String generatedBy
) {
}
