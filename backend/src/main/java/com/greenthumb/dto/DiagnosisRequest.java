package com.greenthumb.dto;

/**
 * 病害识别请求：POST /api/v1/diagnoses/disease
 * 与 frontend/src/api/diagnosis.js 的 diagnoseDisease(data) 对应。
 *
 * @param photoId     必填，已上传的照片 id
 * @param userPlantId 可选；传了就把这次诊断挂到某株植物上，便于时间线展示
 */
public record DiagnosisRequest(Long photoId, Long userPlantId) {
}
