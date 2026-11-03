package com.greenthumb.dto;

/**
 * AI 兜底诊断请求。
 *
 * @param photoId     必填，已上传的照片
 * @param userPlantId 可选，关联的「我的植物」，用于确定物种与养护上下文
 * @param speciesId   可选，前端已确认的物种（优先级高于 userPlantId 推断）
 * @param question    可选，用户补充的症状描述或提问
 */
public record AiDiagnosisRequest(Long photoId, Long userPlantId, Long speciesId, String question) {
}
