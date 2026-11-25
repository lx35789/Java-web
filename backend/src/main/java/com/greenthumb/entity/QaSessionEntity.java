package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 qa_session —— 一次「围绕某盆植物」的连续对话。
 *
 * <p>表在第十周就建好了，但一直没有后端代码，前端 /qa 页面只能走 Mock，
 * 菜单入口因此被摘掉。本表是这次 AI 顾问功能的数据落点。
 *
 * <p>diagnosisContext 存「这次对话从哪次诊断开始」的摘要（病害名、置信度、建议步骤）。
 * 放进会话而不是只放前端 sessionStorage，是为了刷新页面、隔天回来还能接着聊同一件事。
 */
@TableName("`qa_session`")
public class QaSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long userPlantId;
    private String title;
    private Integer messageCount;
    private String diagnosisContext;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getUserPlantId() { return userPlantId; }
    public void setUserPlantId(Long userPlantId) { this.userPlantId = userPlantId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }

    public String getDiagnosisContext() { return diagnosisContext; }
    public void setDiagnosisContext(String diagnosisContext) { this.diagnosisContext = diagnosisContext; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
