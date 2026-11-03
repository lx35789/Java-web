package com.greenthumb.vo;

import java.util.List;

/**
 * 病害识别结果。字段名刻意与 frontend/src/mock/data.js 的 mockDiseaseDiagnosis 保持一致，
 * 这样前端在 Mock / 真实接口之间切换时**不用改代码**。
 * <p>
 * available / reason / latencyMs 是真实接口新增的，前端可以忽略；
 * 模型不可用时 available=false 且 predicted=null，页面走友好提示而不是报错。
 */
public record DiseaseDiagnosisVO(
        Long recordId,
        String diagnosisType,
        String provider,
        Predicted predicted,
        List<Item> topK,
        String symptoms,
        String treatment,
        String prevention,
        String note,
        Boolean available,
        String reason,
        Integer latencyMs,
        Boolean lowConfidence) {

    /** 模型 Top-1，附上知识库里的处理建议 */
    public record Predicted(String code,
                            String nameCn,
                            String nameEn,
                            String category,
                            Double confidence,
                            String severity) {
    }

    /** Top-K 明细，前端画置信度条用 */
    public record Item(Integer rank, String code, String nameCn, Double confidence) {
    }
}
