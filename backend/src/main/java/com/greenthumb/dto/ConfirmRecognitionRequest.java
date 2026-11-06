package com.greenthumb.dto;

/**
 * 确认识别结果：POST /api/v1/recognitions/{id}/confirm
 * <p>
 * 对应 frontend/src/api/recognition.js 的 confirmRecognition。
 * 前端在「加入我的植物」时传 createPlant=true，并把用户填的位置/光照带过来。
 *
 * @param speciesId     用户最终选定的物种（可以不是 Top-1，允许纠正模型）
 * @param createPlant   是否同时创建一盆「我的植物」
 * @param userPlantForm 创建植物时的表单（createPlant=true 时使用）
 */
public record ConfirmRecognitionRequest(
        Long speciesId,
        Boolean createPlant,
        UserPlantForm userPlantForm
) {
    /** 与 CreatePlantRequest 字段对齐，但只有这几个是前端会传的，其余留空由物种默认值补 */
    public record UserPlantForm(
            String nickname,
            String location,
            String lightLevel,
            String potSize,
            String potMaterial,
            String soilType,
            /** yyyy-MM-dd */
            String acquiredDate,
            String note
    ) {
    }
}
