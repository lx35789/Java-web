package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对应 db/schema.sql 的 plant_species 表。
 * <p>
 * JSON 类型的列（aliases / toxic_to / care_tips / season_coefficients）在这里统一映射成 String，
 * 由 SpeciesService 用 Jackson 解析成前端要的数组/对象。
 * 这样做比注册 TypeHandler 简单，而且出问题时日志里能直接看到原始 JSON。
 */
@TableName("`plant_species`")
public class PlantSpeciesEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String commonName;
    private String commonNameEn;
    private String scientificName;
    /** JSON: ["魔鬼藤","黄金葛"] */
    private String aliases;
    private String family;
    private String emoji;
    private String difficulty;

    private Integer baseWaterIntervalDays;
    private Integer waterAmountMl;
    private String lightRequirement;
    private String humidityRequirement;
    private BigDecimal tempMinC;
    private BigDecimal tempMaxC;
    private Integer fertilizerIntervalDays;
    private String fertilizerType;
    private String soilType;
    private String growthRate;
    private Integer maxHeightCm;

    private String toxicity;
    /** JSON: [{"target":"cat","level":"HIGH"}] */
    private String toxicTo;

    private Integer modelClassIndex;
    private String recognitionProvider;

    private String imageUrl;
    private String description;
    /** JSON: ["避免积水","冬季减少浇水"] */
    private String careTips;
    /** JSON: {"SPRING":1.0,"SUMMER":0.8,...} */
    private String seasonCoefficients;
    private String source;
    private String sourceUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getCommonNameEn() {
        return commonNameEn;
    }

    public void setCommonNameEn(String commonNameEn) {
        this.commonNameEn = commonNameEn;
    }

    public String getScientificName() {
        return scientificName;
    }

    public void setScientificName(String scientificName) {
        this.scientificName = scientificName;
    }

    public String getAliases() {
        return aliases;
    }

    public void setAliases(String aliases) {
        this.aliases = aliases;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getBaseWaterIntervalDays() {
        return baseWaterIntervalDays;
    }

    public void setBaseWaterIntervalDays(Integer baseWaterIntervalDays) {
        this.baseWaterIntervalDays = baseWaterIntervalDays;
    }

    public Integer getWaterAmountMl() {
        return waterAmountMl;
    }

    public void setWaterAmountMl(Integer waterAmountMl) {
        this.waterAmountMl = waterAmountMl;
    }

    public String getLightRequirement() {
        return lightRequirement;
    }

    public void setLightRequirement(String lightRequirement) {
        this.lightRequirement = lightRequirement;
    }

    public String getHumidityRequirement() {
        return humidityRequirement;
    }

    public void setHumidityRequirement(String humidityRequirement) {
        this.humidityRequirement = humidityRequirement;
    }

    public BigDecimal getTempMinC() {
        return tempMinC;
    }

    public void setTempMinC(BigDecimal tempMinC) {
        this.tempMinC = tempMinC;
    }

    public BigDecimal getTempMaxC() {
        return tempMaxC;
    }

    public void setTempMaxC(BigDecimal tempMaxC) {
        this.tempMaxC = tempMaxC;
    }

    public Integer getFertilizerIntervalDays() {
        return fertilizerIntervalDays;
    }

    public void setFertilizerIntervalDays(Integer fertilizerIntervalDays) {
        this.fertilizerIntervalDays = fertilizerIntervalDays;
    }

    public String getFertilizerType() {
        return fertilizerType;
    }

    public void setFertilizerType(String fertilizerType) {
        this.fertilizerType = fertilizerType;
    }

    public String getSoilType() {
        return soilType;
    }

    public void setSoilType(String soilType) {
        this.soilType = soilType;
    }

    public String getGrowthRate() {
        return growthRate;
    }

    public void setGrowthRate(String growthRate) {
        this.growthRate = growthRate;
    }

    public Integer getMaxHeightCm() {
        return maxHeightCm;
    }

    public void setMaxHeightCm(Integer maxHeightCm) {
        this.maxHeightCm = maxHeightCm;
    }

    public String getToxicity() {
        return toxicity;
    }

    public void setToxicity(String toxicity) {
        this.toxicity = toxicity;
    }

    public String getToxicTo() {
        return toxicTo;
    }

    public void setToxicTo(String toxicTo) {
        this.toxicTo = toxicTo;
    }

    public Integer getModelClassIndex() {
        return modelClassIndex;
    }

    public void setModelClassIndex(Integer modelClassIndex) {
        this.modelClassIndex = modelClassIndex;
    }

    public String getRecognitionProvider() {
        return recognitionProvider;
    }

    public void setRecognitionProvider(String recognitionProvider) {
        this.recognitionProvider = recognitionProvider;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCareTips() {
        return careTips;
    }

    public void setCareTips(String careTips) {
        this.careTips = careTips;
    }

    public String getSeasonCoefficients() {
        return seasonCoefficients;
    }

    public void setSeasonCoefficients(String seasonCoefficients) {
        this.seasonCoefficients = seasonCoefficients;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
