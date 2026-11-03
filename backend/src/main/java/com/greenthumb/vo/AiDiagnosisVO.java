package com.greenthumb.vo;

import java.util.List;

/**
 * AI 兜底诊断结果。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>{@code source} —— 结论来自哪条链路：{@code MODEL}（自训 CNN）、
 *       {@code VLM}（大模型视觉）、{@code UNAVAILABLE}（都不可用）</li>
 *   <li>{@code inKnowledgeBase} —— 该病害是否在本地知识库里。
 *       为 {@code false} 时说明是大模型推测的新病害，前端必须显著标注</li>
 *   <li>{@code evidence} —— 视觉依据。强制模型写"看到了什么"，
 *       这是压制幻觉最有效的一招：编造的结论通常说不出具体依据</li>
 *   <li>{@code needMoreInfo} —— 置信度不足时置 true，前端提示用户补充描述，
 *       而不是硬给一个错误结论</li>
 * </ul>
 */
public record AiDiagnosisVO(
        String source,
        String diseaseName,
        String category,
        Double confidence,
        Boolean inKnowledgeBase,
        List<String> evidence,
        List<String> causes,
        List<String> treatment,
        List<String> prevention,
        Boolean needMoreInfo,
        String followUpQuestion,
        String disclaimer,
        String modelNote,
        List<Item> topK
) {
    /** CNN 的 Top-K 候选，走大模型时为空 */
    public record Item(String code, String nameCn, Double confidence) {
    }
}
