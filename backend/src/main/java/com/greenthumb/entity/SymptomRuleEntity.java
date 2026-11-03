package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 symptom_rule 表：症状 → 病因 的判因规则。
 * <p>
 * 规则入库而不是写在代码里，是为了能不改代码就调权重（详见 docs/03-症状诊断规则库.md）。
 */
@TableName("`symptom_rule`")
public class SymptomRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String symptomCode;
    private String causeCode;
    /** 触发条件 JSON，多字段为 AND */
    private String conditionJson;
    /** 命中权重 0.00-1.00，同病因可累加 */
    private BigDecimal weight;
    private String explainTpl;
    private Integer priority;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymptomCode() {
        return symptomCode;
    }

    public void setSymptomCode(String symptomCode) {
        this.symptomCode = symptomCode;
    }

    public String getCauseCode() {
        return causeCode;
    }

    public void setCauseCode(String causeCode) {
        this.causeCode = causeCode;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public String getExplainTpl() {
        return explainTpl;
    }

    public void setExplainTpl(String explainTpl) {
        this.explainTpl = explainTpl;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
