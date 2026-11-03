package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对应 db/schema.sql 的 recognition_record 表 */
@TableName("`recognition_record`")
public class RecognitionRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long photoId;
    private String provider;
    private String modelVersion;
    private Long top1SpeciesId;
    private BigDecimal top1Confidence;
    private Integer latencyMs;
    private Integer confirmed;
    private Long userPlantId;
    private LocalDateTime createdAt;

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

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Long getTop1SpeciesId() {
        return top1SpeciesId;
    }

    public void setTop1SpeciesId(Long top1SpeciesId) {
        this.top1SpeciesId = top1SpeciesId;
    }

    public BigDecimal getTop1Confidence() {
        return top1Confidence;
    }

    public void setTop1Confidence(BigDecimal top1Confidence) {
        this.top1Confidence = top1Confidence;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Integer getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Integer confirmed) {
        this.confirmed = confirmed;
    }

    public Long getUserPlantId() {
        return userPlantId;
    }

    public void setUserPlantId(Long userPlantId) {
        this.userPlantId = userPlantId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
