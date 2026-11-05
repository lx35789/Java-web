package com.greenthumb.dto;

/**
 * 病害识别请求：POST /api/v1/diagnoses/disease
 * 与 frontend/src/api/diagnosis.js 的 diagnoseDisease(data) 对应。
 *
 * @param photoId     必填，已上传的照片 id
 * @param userPlantId 可选；传了就把这次诊断挂到某株植物上，便于时间线展示
 * @param speciesName 可选；物种中文名（如「芦荟」）。用于路由到**物种专用模型**：
 *                    芦荟有专用 5 类模型，不传会回退通用 29 类模型，
 *                    同一张图准确率会从 ~75% 掉到 ~8%。
 *                    调用方通常传第 1 步识别出的物种名，或在已知植株品种时传。
 */
public record DiagnosisRequest(Long photoId, Long userPlantId, String speciesName) {
}
