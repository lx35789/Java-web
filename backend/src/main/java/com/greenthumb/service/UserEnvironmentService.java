package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenthumb.entity.UserEnvironmentEntity;
import com.greenthumb.mapper.UserEnvironmentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户环境设置（城市 / 空调 / 暖气 / 室内湿度）。
 *
 * <p>这些字段是养护计划系数的输入源（docs/02 3.4 节）：
 * 空调房 × 0.9、暖气 × 1.1 等。没有这份配置，计划只能按物种基准算，
 * 「每个数字都能追溯」就成了半句话。
 *
 * <p>城市变更时自动做地理编码补经纬度 —— 让用户填个"杭州"就够，
 * 不用自己去找坐标。
 */
@Service
public class UserEnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(UserEnvironmentService.class);

    private final UserEnvironmentMapper envMapper;
    private final WeatherService weatherService;

    public UserEnvironmentService(UserEnvironmentMapper envMapper, WeatherService weatherService) {
        this.envMapper = envMapper;
        this.weatherService = weatherService;
    }

    public Map<String, Object> get(Long userId) {
        UserEnvironmentEntity env = find(userId);
        if (env == null) {
            // 没有记录时返回空壳而不是 404：前端表单要能渲染默认值
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("city", null);
            empty.put("latitude", null);
            empty.put("longitude", null);
            empty.put("hasAirConditioner", false);
            empty.put("hasHeating", false);
            empty.put("indoorHumidityLevel", "MEDIUM");
            empty.put("weatherSyncEnabled", false);
            empty.put("weatherSyncedAt", null);
            return empty;
        }
        return toMap(env);
    }

    @Transactional
    public Map<String, Object> update(Long userId, Map<String, Object> body) {
        UserEnvironmentEntity env = find(userId);
        boolean created = false;
        if (env == null) {
            env = new UserEnvironmentEntity();
            env.setUserId(userId);
            env.setTimezone("Asia/Shanghai");
            env.setWeatherSyncEnabled(0);
            created = true;
        }

        if (body != null) {
            Object city = body.get("city");
            if (city != null) {
                String c = String.valueOf(city).trim();
                boolean changed = env.getCity() == null || !env.getCity().equals(c);
                env.setCity(c.isEmpty() ? null : c);
                // 城市变了才重新解析坐标，避免每次保存都打一次地理编码
                if (changed && !c.isEmpty()) {
                    double[] ll = weatherService.geocode(c);
                    if (ll != null) {
                        env.setLatitude(BigDecimal.valueOf(ll[0]));
                        env.setLongitude(BigDecimal.valueOf(ll[1]));
                    } else {
                        log.warn("城市「{}」解析不到坐标，天气功能将不可用", c);
                        env.setLatitude(null);
                        env.setLongitude(null);
                    }
                }
            }
            if (body.containsKey("latitude") && body.get("latitude") instanceof Number n) {
                env.setLatitude(BigDecimal.valueOf(n.doubleValue()));
            }
            if (body.containsKey("longitude") && body.get("longitude") instanceof Number n) {
                env.setLongitude(BigDecimal.valueOf(n.doubleValue()));
            }
            if (body.containsKey("hasAirConditioner")) {
                env.setHasAirConditioner(boolInt(body.get("hasAirConditioner")));
            }
            if (body.containsKey("hasHeating")) {
                env.setHasHeating(boolInt(body.get("hasHeating")));
            }
            Object hum = body.get("indoorHumidityLevel");
            if (hum != null) {
                env.setIndoorHumidityLevel(String.valueOf(hum).toUpperCase());
            }
            if (body.containsKey("weatherSyncEnabled")) {
                env.setWeatherSyncEnabled(boolInt(body.get("weatherSyncEnabled")));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (created) {
            env.setCreatedAt(now);
            env.setUpdatedAt(now);
            envMapper.insert(env);
        } else {
            env.setUpdatedAt(now);
            envMapper.updateById(env);
        }
        return toMap(env);
    }

    private UserEnvironmentEntity find(Long userId) {
        return envMapper.selectOne(new LambdaQueryWrapper<UserEnvironmentEntity>()
                .eq(UserEnvironmentEntity::getUserId, userId).last("limit 1"));
    }

    private static int boolInt(Object o) {
        if (o instanceof Boolean b) return b ? 1 : 0;
        if (o instanceof Number n) return n.intValue() != 0 ? 1 : 0;
        return 0;
    }

    private static Map<String, Object> toMap(UserEnvironmentEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("city", e.getCity());
        m.put("latitude", e.getLatitude());
        m.put("longitude", e.getLongitude());
        m.put("hasAirConditioner", e.getHasAirConditioner() != null && e.getHasAirConditioner() == 1);
        m.put("hasHeating", e.getHasHeating() != null && e.getHasHeating() == 1);
        m.put("indoorHumidityLevel", e.getIndoorHumidityLevel());
        m.put("weatherSyncEnabled", e.getWeatherSyncEnabled() != null && e.getWeatherSyncEnabled() == 1);
        m.put("weatherSyncedAt", e.getWeatherSyncedAt() == null ? null
                : e.getWeatherSyncedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return m;
    }
}
