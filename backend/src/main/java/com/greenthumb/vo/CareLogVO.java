package com.greenthumb.vo;

/**
 * 养护记录视图，字段与 frontend/src/mock/data.js 的 mockCareLogs 对齐。
 */
public record CareLogVO(
        Long id,
        Long userPlantId,
        Long taskId,
        String logType,
        Double amount,
        String unit,
        String detail,
        Long photoId,
        String loggedAt,
        String plantNickname,
        String speciesName,
        String speciesEmoji
) {
}
