package com.greenthumb.dto;

/** 更新诊断记录状态：PUT /api/v1/diagnoses/{id}/status，取值 OPEN / TREATING / RESOLVED */
public record DiagnosisStatusRequest(String status) {
}
