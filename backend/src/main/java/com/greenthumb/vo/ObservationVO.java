package com.greenthumb.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条状态观测，用于曲线与时间轴。
 *
 * @param id          观测 id
 * @param observedAt  观测时刻
 * @param source      PHOTO / MANUAL / DIAGNOSIS / REMINDER
 * @param leafScore   1 饱满 2 一般 3 发蔫 4 很差
 * @param growthScore 1 有新叶 2 无变化 3 有落叶
 * @param yellowLeaf  0 无 1 少 2 中 3 多
 * @param greenRatio  绿色像素占比
 * @param yellowRatio 黄色像素占比
 * @param brownRatio  褐色像素占比
 * @param healthIndex 0-100 综合健康指数
 * @param note        备注
 */
public record ObservationVO(
        Long id,
        LocalDateTime observedAt,
        String source,
        Integer leafScore,
        Integer growthScore,
        Integer yellowLeaf,
        BigDecimal greenRatio,
        BigDecimal yellowRatio,
        BigDecimal brownRatio,
        Integer healthIndex,
        String note
) {}
