package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 care_plan 表 —— 养护计划。
 * <p>
 * 一盆植物 × 一种养护类型（浇水/施肥/修剪/换盆）各有一条计划。
 * 间隔不是固定值：effective_interval_days 是由 base_interval_days
 * 乘上一堆系数算出来的（季节、光照、温度、湿度、盆材质、是否空调房），
 * 系数明细存 coefficient_detail，前端展示"为什么是 5 天"就靠它。
 */
@TableName("`care_plan`")
public class CarePlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long userPlantId;
    /** WATER / FERTILIZE / PRUNE / REPOT */
    private String planType;
    /** SPRING / SUMMER / AUTUMN / WINTER */
    private String season;
    private Integer baseIntervalDays;
    private Integer effectiveIntervalDays;
    /** JSON：各系数原值 */
    private String coefficientDetail;
    /** JSON：生成计划时的天气快照 */
    private String weatherSnapshot;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer isActive;
    /** RULE / LLM */
    private String generatedBy;
    private String llmExplain;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getUserPlantId() { return userPlantId; }
    public void setUserPlantId(Long userPlantId) { this.userPlantId = userPlantId; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public Integer getBaseIntervalDays() { return baseIntervalDays; }
    public void setBaseIntervalDays(Integer baseIntervalDays) { this.baseIntervalDays = baseIntervalDays; }

    public Integer getEffectiveIntervalDays() { return effectiveIntervalDays; }
    public void setEffectiveIntervalDays(Integer effectiveIntervalDays) { this.effectiveIntervalDays = effectiveIntervalDays; }

    public String getCoefficientDetail() { return coefficientDetail; }
    public void setCoefficientDetail(String coefficientDetail) { this.coefficientDetail = coefficientDetail; }

    public String getWeatherSnapshot() { return weatherSnapshot; }
    public void setWeatherSnapshot(String weatherSnapshot) { this.weatherSnapshot = weatherSnapshot; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }

    public String getLlmExplain() { return llmExplain; }
    public void setLlmExplain(String llmExplain) { this.llmExplain = llmExplain; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
