package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 care_log 表 —— 养护记录（实际做过的动作）。
 * <p>
 * 和 care_task 的区别：task 是"该做什么"，log 是"做了什么"。
 * 用户可能做计划外的操作（task_id 为空），所以两者不是一一对应。
 */
@TableName("`care_log`")
public class CareLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long userPlantId;
    /** 可空：计划外操作没有对应任务 */
    private Long taskId;
    /** WATER / FERTILIZE / PRUNE / REPOT / PEST / OTHER */
    private String logType;
    private BigDecimal amount;
    private String unit;
    private String detail;
    private Long photoId;
    private LocalDateTime loggedAt;
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getUserPlantId() { return userPlantId; }
    public void setUserPlantId(Long userPlantId) { this.userPlantId = userPlantId; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getLogType() { return logType; }
    public void setLogType(String logType) { this.logType = logType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public Long getPhotoId() { return photoId; }
    public void setPhotoId(Long photoId) { this.photoId = photoId; }

    public LocalDateTime getLoggedAt() { return loggedAt; }
    public void setLoggedAt(LocalDateTime loggedAt) { this.loggedAt = loggedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
