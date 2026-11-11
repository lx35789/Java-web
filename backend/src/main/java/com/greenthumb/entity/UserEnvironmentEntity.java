package com.greenthumb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对应 db/schema.sql 的 user_environment 表 —— 用户所处环境 */
@TableName("`user_environment`")
public class UserEnvironmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timezone;
    private String climateZone;
    /** 1=有空调 0=无 */
    private Integer hasAirConditioner;
    private Integer hasHeating;
    private String indoorHumidityLevel;
    private Integer weatherSyncEnabled;
    private LocalDateTime weatherSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getClimateZone() { return climateZone; }
    public void setClimateZone(String climateZone) { this.climateZone = climateZone; }

    public Integer getHasAirConditioner() { return hasAirConditioner; }
    public void setHasAirConditioner(Integer hasAirConditioner) { this.hasAirConditioner = hasAirConditioner; }

    public Integer getHasHeating() { return hasHeating; }
    public void setHasHeating(Integer hasHeating) { this.hasHeating = hasHeating; }

    public String getIndoorHumidityLevel() { return indoorHumidityLevel; }
    public void setIndoorHumidityLevel(String indoorHumidityLevel) { this.indoorHumidityLevel = indoorHumidityLevel; }

    public Integer getWeatherSyncEnabled() { return weatherSyncEnabled; }
    public void setWeatherSyncEnabled(Integer weatherSyncEnabled) { this.weatherSyncEnabled = weatherSyncEnabled; }

    public LocalDateTime getWeatherSyncedAt() { return weatherSyncedAt; }
    public void setWeatherSyncedAt(LocalDateTime weatherSyncedAt) { this.weatherSyncedAt = weatherSyncedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
