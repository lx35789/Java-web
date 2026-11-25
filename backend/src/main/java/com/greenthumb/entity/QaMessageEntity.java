package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 qa_message —— 一轮问答里的单条消息。
 *
 * <p>model / latencyMs 用于在气泡下方显示「qwen-plus · 2.3s」，
 * 也让「AI 用量统计」有数可查；feedback 记有用/没用。
 */
@TableName("`qa_message`")
public class QaMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    /** USER / ASSISTANT */
    private String role;
    private String content;
    private String retrievedChunks;
    private String citations;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer latencyMs;
    private Integer feedback;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getRetrievedChunks() { return retrievedChunks; }
    public void setRetrievedChunks(String retrievedChunks) { this.retrievedChunks = retrievedChunks; }

    public String getCitations() { return citations; }
    public void setCitations(String citations) { this.citations = citations; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public Integer getFeedback() { return feedback; }
    public void setFeedback(Integer feedback) { this.feedback = feedback; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
