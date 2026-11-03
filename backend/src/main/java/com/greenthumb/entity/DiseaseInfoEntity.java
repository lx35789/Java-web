package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 disease_info 表：病害类别 → 处理建议。
 * <p>
 * code 必须与 AI 模型 labels.json 里的类别码**完全一致**，
 * 这样 AiClientService 返回什么，这里就能查到什么。
 * 查不到时不允许报错，只把 code 原样返回给前端（模型进化比知识库快）。
 */
@TableName("`disease_info`")
public class DiseaseInfoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模型类别码，如 Aloe_Anthracnose */
    private String code;
    private String nameCn;
    private String nameEn;
    private String eppoCode;
    /** FUNGAL / BACTERIAL / PEST / PHYSIOLOGICAL */
    private String category;
    private String pathogen;
    private String symptoms;
    private String causes;
    private String treatment;
    private String prevention;
    private String severity;
    /** JSON 字符串：[1,7]；NULL 表示通用 */
    private String applicableSpecies;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNameCn() {
        return nameCn;
    }

    public void setNameCn(String nameCn) {
        this.nameCn = nameCn;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getEppoCode() {
        return eppoCode;
    }

    public void setEppoCode(String eppoCode) {
        this.eppoCode = eppoCode;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPathogen() {
        return pathogen;
    }

    public void setPathogen(String pathogen) {
        this.pathogen = pathogen;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getCauses() {
        return causes;
    }

    public void setCauses(String causes) {
        this.causes = causes;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }

    public String getPrevention() {
        return prevention;
    }

    public void setPrevention(String prevention) {
        this.prevention = prevention;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getApplicableSpecies() {
        return applicableSpecies;
    }

    public void setApplicableSpecies(String applicableSpecies) {
        this.applicableSpecies = applicableSpecies;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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
