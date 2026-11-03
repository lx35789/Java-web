package com.greenthumb.vo;

import java.util.List;

/**
 * 症状诊断结果（生理性：黄叶 / 焦边 / 烂根…）。
 * 与 frontend/src/mock/data.js 的 mockSymptomDiagnosis / mockSymptomDiagnosisLowConfidence 对齐。
 * <p>
 * 规则命不中时不是"失败"：needMoreInfo=true + 追问清单，把用户引导到 AI 问答。
 */
public record SymptomDiagnosisVO(
        Long recordId,
        String diagnosisType,
        String confidenceLevel,
        List<Candidate> candidates,
        Boolean needMoreInfo,
        List<String> followUpQuestions,
        String fallbackSuggestion) {

    public record Candidate(Integer rank,
                            String code,
                            String nameCn,
                            String category,
                            Double confidence,
                            String urgency,
                            String explain,
                            String actionPlan) {
    }
}
