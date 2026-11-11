package com.greenthumb.vo;

/**
 * 养护任务视图，字段与 frontend/src/mock/data.js 的 mockCareTasks 对齐。
 *
 * @param status 库里的 PENDING/DONE/SKIPPED，或运行期推出的 OVERDUE
 * @param dueDate yyyy-MM-dd（表里是 date，无时分）
 */
public record CareTaskVO(
        Long id,
        Long planId,
        Long userPlantId,
        String taskType,
        String dueDate,
        String status,
        String completedAt,
        String plantNickname,
        String speciesName,
        String speciesEmoji,
        Long careLogId,
        String note
) {
}
