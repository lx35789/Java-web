package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 plant_observation 表 —— 植物「变成了什么样」的时间序列。
 *
 * <p>与 care_log 互补：care_log 记「人做了什么」，本表记「植物怎么样」。
 * 没有这张表就只能开环养护：计划定了就再也不变，叶子蔫了系统也不知道。
 *
 * <p>三个主观维度各 3~4 档，用户几秒钟能答完，服务端合成 health_index（0-100）
 * 用于画曲线；照片来源的观测还会带上客观色彩占比，两者互为校验。
 */
@TableName("`plant_observation`")
public class PlantObservationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userPlantId;
    private LocalDateTime observedAt;

    /** PHOTO / MANUAL / DIAGNOSIS / REMINDER */
    private String source;
    private Long photoId;
    private Long diagnosisId;

    /** 1 饱满 2 一般 3 发蔫 4 很差 */
    private Integer leafScore;
    /** 1 有新叶 2 无变化 3 有落叶 */
    private Integer growthScore;
    /** 0 无 1 少 2 中 3 多 */
    private Integer yellowLeaf;

    /** 照片自动计算的像素占比 */
    private BigDecimal greenRatio;
    private BigDecimal yellowRatio;
    private BigDecimal brownRatio;

    /** 0-100，画曲线用；非空 */
    private Integer healthIndex;

    private String note;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserPlantId() { return userPlantId; }
    public void setUserPlantId(Long userPlantId) { this.userPlantId = userPlantId; }

    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Long getPhotoId() { return photoId; }
    public void setPhotoId(Long photoId) { this.photoId = photoId; }

    public Long getDiagnosisId() { return diagnosisId; }
    public void setDiagnosisId(Long diagnosisId) { this.diagnosisId = diagnosisId; }

    public Integer getLeafScore() { return leafScore; }
    public void setLeafScore(Integer leafScore) { this.leafScore = leafScore; }

    public Integer getGrowthScore() { return growthScore; }
    public void setGrowthScore(Integer growthScore) { this.growthScore = growthScore; }

    public Integer getYellowLeaf() { return yellowLeaf; }
    public void setYellowLeaf(Integer yellowLeaf) { this.yellowLeaf = yellowLeaf; }

    public BigDecimal getGreenRatio() { return greenRatio; }
    public void setGreenRatio(BigDecimal greenRatio) { this.greenRatio = greenRatio; }

    public BigDecimal getYellowRatio() { return yellowRatio; }
    public void setYellowRatio(BigDecimal yellowRatio) { this.yellowRatio = yellowRatio; }

    public BigDecimal getBrownRatio() { return brownRatio; }
    public void setBrownRatio(BigDecimal brownRatio) { this.brownRatio = brownRatio; }

    public Integer getHealthIndex() { return healthIndex; }
    public void setHealthIndex(Integer healthIndex) { this.healthIndex = healthIndex; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
