package com.greenthumb.vo;

import java.util.List;

/**
 * 症状诊断结果（生理性：黄叶 / 焦边 / 烂根…）。
 * <p>
 * ★ 字段名与前端 mockSymptomDiagnosis / DiagnosisEvidence.vue 严格对齐：
 * causeCode / evidence[] / actionPlan[] / hitRuleIds[]。
 * 早一版用的是 code / explain(字符串) / actionPlan(字符串)，
 * 导致前端「判断依据」「建议这样做」整块渲染不出来（v-for 拿不到数组），
 * 命中规则编号也无法展示 —— 这里按前端契约修正。
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
        String fallbackSuggestion,
        /** 综合建议（前端「综合建议」区块）；信息不足时为 null */
        String suggestion) {

    public record Candidate(Integer rank,
                            String causeCode,
                            String nameCn,
                            String category,
                            Double confidence,
                            String urgency,
                            /** 判断依据：命中规则渲染后的说明，逐条展示 */
                            List<String> evidence,
                            /** 处理步骤：把 actionPlan 多行文本拆成数组，前端用有序列表渲染 */
                            List<String> actionPlan,
                            /** 命中的规则 ID，前端展示为「命中规则 #1、#3」 */
                            List<Long> hitRuleIds) {
    }
}
