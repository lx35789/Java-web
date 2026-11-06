package com.greenthumb.vo;

/**
 * 确认结果：POST /api/v1/recognitions/{id}/confirm
 *
 * @param userPlantId       新建的「我的植物」id，未创建时为 null
 * @param carePlanGenerated 是否已生成养护计划。当前养护计划模块尚未实现，恒为 false
 *                          —— 前端据此提示「养护计划稍后可在植物详情生成」，避免谎报成功
 * @param speciesId         最终确认的物种 id
 */
public record ConfirmRecognitionVO(
        Long userPlantId,
        boolean carePlanGenerated,
        Long speciesId
) {
}
