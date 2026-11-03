package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 disease_record 表：病害/症状诊断记录。
 * <p>
 * 一条记录既能存"病理性"（模型输出的病害），也能存"生理性"（症状规则推出来的原因），
 * 靠 diagnosis_type 区分，这样列表页可以统一展示。
 */
@TableName("`disease_record`")
public class DiseaseRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long userPlantId;
    private Long photoId;
    /** SELF_MODEL / NONE */
    private String provider;
    private Long diseaseInfoId;
    /** 模型原始类别码，即使知识库没有也要存下来 */
    private String predictedCode;
    private BigDecimal confidence;
    /** JSON：Top-K 明细 */
    private String topK;
    /** DISEASE / SYMPTOM */
    private String diagnosisType;
    /** JSON：症状路径的输入 */
    private String symptomTags;
    private String suggestion;
    /** OPEN / TREATING / RESOLVED */
    private String status;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getUserPlantId() {
        return userPlantId;
    }

    public void setUserPlantId(Long userPlantId) {
        this.userPlantId = userPlantId;
    }

    public Long getPhotoId() {
        return photoId;
    }

    public void setPhotoId(Long photoId) {
        this.photoId = photoId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Long getDiseaseInfoId() {
        return diseaseInfoId;
    }

    public void setDiseaseInfoId(Long diseaseInfoId) {
        this.diseaseInfoId = diseaseInfoId;
    }

    public String getPredictedCode() {
        return predictedCode;
    }

    public void setPredictedCode(String predictedCode) {
        this.predictedCode = predictedCode;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getTopK() {
        return topK;
    }

    public void setTopK(String topK) {
        this.topK = topK;
    }

    public String getDiagnosisType() {
        return diagnosisType;
    }

    public void setDiagnosisType(String diagnosisType) {
        this.diagnosisType = diagnosisType;
    }

    public String getSymptomTags() {
        return symptomTags;
    }

    public void setSymptomTags(String symptomTags) {
        this.symptomTags = symptomTags;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
