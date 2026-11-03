package com.greenthumb.vo;

/**
 * 诊断记录列表项。字段与 frontend/src/mock/data.js 的 mockDiagnosisRecords 一致。
 * 列表页只给摘要，点进去再调 GET /diagnoses/{id} 拿 detail。
 */
public record DiagnosisSummaryVO(
        Long id,
        Long userPlantId,
        String plantNickname,
        String diagnosisType,
        String createdAt,
        String summary,
        Double confidence,
        String status,
        String code) {
}
