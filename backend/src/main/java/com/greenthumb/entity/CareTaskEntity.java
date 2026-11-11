package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 care_task 表 —— 养护任务（日历上的一条）。
 * <p>
 * 计划描述"多久一次"，任务描述"这一次"。任务由计划按间隔展开而来，
 * 用户完成/跳过/推迟操作的都是任务。
 * <p>
 * 注意：本表**没有** deleted 字段（见 schema.sql），任务不做软删除，
 * 作废用 status 表达（SKIPPED），所以这里不能加 @TableLogic。
 */
@TableName("`care_task`")
public class CareTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;
    private Long userId;
    private Long userPlantId;
    /** WATER / FERTILIZE / PRUNE / REPOT */
    private String taskType;
    /** 表里是 date 类型，只有日期没有时分 */
    private LocalDate dueDate;
    /** PENDING / DONE / SKIPPED / OVERDUE */
    private String status;
    private LocalDateTime completedAt;
    private Long careLogId;
    private LocalDateTime remindAt;
    private Integer reminded;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getUserPlantId() { return userPlantId; }
    public void setUserPlantId(Long userPlantId) { this.userPlantId = userPlantId; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Long getCareLogId() { return careLogId; }
    public void setCareLogId(Long careLogId) { this.careLogId = careLogId; }

    public LocalDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(LocalDateTime remindAt) { this.remindAt = remindAt; }

    public Integer getReminded() { return reminded; }
    public void setReminded(Integer reminded) { this.reminded = reminded; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
