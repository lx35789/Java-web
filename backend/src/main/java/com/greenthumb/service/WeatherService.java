package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.entity.UserEnvironmentEntity;
import com.greenthumb.mapper.UserEnvironmentMapper;
import com.greenthumb.vo.WeatherVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 天气：Open-Meteo 免 Key，按用户城市的经纬度取实时温湿度，并算出对养护的影响。
 *
 * <p>为什么用 Open-Meteo 而不是国内天气 API：
 * 无需注册、无需 API Key，不会因为 key 过期导致演示时挂掉；免费额度足够。
 * 代价是没有中国市级行政区的精细数据，所以城市名 → 坐标要靠地理编码。
 *
 * <p>缓存策略：同一次请求内不重复调外网；结果写回 user_environment.weather_synced_at，
 * 15 分钟内复用，避免用户每刷新一次页面就打一次外部接口。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    /** 天气缓存有效期，避免频繁请求外部接口 */
    private static final long CACHE_MINUTES = 15;

    private final UserEnvironmentMapper envMapper;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 必须显式指定 HTTP/1.1。
     *
     * JDK HttpClient 默认会尝试 h2c 明文升级（Upgrade: h2c），部分 CDN 不认这个头，
     * 表现为 GET 侥幸成功、带 body 的 POST 直接 Connection reset。
     * 这个坑在对接本地 uvicorn 时已经踩过一次，对外部服务同样预防。
     */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public WeatherService(UserEnvironmentMapper envMapper) {
        this.envMapper = envMapper;
    }

    /* ==================== 对外 ==================== */

    /** 读取当前天气：有缓存用缓存，没有就实时拉一次 */
    public WeatherVO current(Long userId) {
        UserEnvironmentEntity env = envOf(userId);
        if (env == null || env.getLatitude() == null || env.getLongitude() == null) {
            // 没有坐标不算错误：前端据此提示"先去设置城市"，而不是弹一个红色报错
            return WeatherVO.unavailable("尚未设置城市，无法获取天气。请先在「环境设置」里填写城市。");
        }
        if (env.getWeatherSyncedAt() != null
                && env.getWeatherSyncedAt().isAfter(LocalDateTime.now().minusMinutes(CACHE_MINUTES))) {
            WeatherVO cached = fetch(env);
            if (cached != null) {
                return cached;
            }
        }
        WeatherVO fresh = fetch(env);
        if (fresh == null) {
            return WeatherVO.unavailable("暂时连不上天气服务，请稍后重试。");
        }
        touchSyncedAt(env);
        return fresh;
    }

    /** 强制同步（忽略缓存） */
    public WeatherVO sync(Long userId) {
        UserEnvironmentEntity env = envOf(userId);
        if (env == null || env.getLatitude() == null || env.getLongitude() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "请先在「环境设置」里填写城市，系统会自动解析坐标");
        }
        WeatherVO vo = fetch(env);
        if (vo == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "天气服务暂时不可用，请稍后重试");
        }
        touchSyncedAt(env);
        return vo;
    }

    /**
     * 城市名 → 经纬度（Open-Meteo geocoding）。
     * 用户只填"杭州"时用它补全坐标；解析不到就返回 null，由调用方决定怎么提示。
     */
    public double[] geocode(String city) {
        if (city == null || city.isBlank()) return null;
        try {
            String url = GEOCODE_URL + "?name=" + URLEncoder.encode(city.trim(), StandardCharsets.UTF_8)
                    + "&count=1&language=zh&format=json";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = json.readTree(resp.body());
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) return null;
            JsonNode first = results.get(0);
            double lat = first.path("latitude").asDouble();
            double lon = first.path("longitude").asDouble();
            if (lat == 0 && lon == 0) return null;
            return new double[]{lat, lon};
        } catch (Exception e) {
            log.warn("地理编码失败 city={}：{}", city, e.toString());
            return null;
        }
    }

    /* ==================== 内部 ==================== */

    private UserEnvironmentEntity envOf(Long userId) {
        return envMapper.selectOne(new LambdaQueryWrapper<UserEnvironmentEntity>()
                .eq(UserEnvironmentEntity::getUserId, userId).last("limit 1"));
    }

    private void touchSyncedAt(UserEnvironmentEntity env) {
        try {
            UserEnvironmentEntity patch = new UserEnvironmentEntity();
            patch.setId(env.getId());
            patch.setWeatherSyncedAt(LocalDateTime.now());
            envMapper.updateById(patch);
        } catch (Exception e) {
            log.debug("更新时间戳失败（不影响本次结果）：{}", e.toString());
        }
    }

    /** 调 Open-Meteo 拿实时温湿度 */
    private WeatherVO fetch(UserEnvironmentEntity env) {
        try {
            String url = FORECAST_URL
                    + "?latitude=" + env.getLatitude()
                    + "&longitude=" + env.getLongitude()
                    + "&current=temperature_2m,relative_humidity_2m,precipitation,weather_code"
                    + "&timezone=" + URLEncoder.encode(
                        env.getTimezone() == null ? "Asia/Shanghai" : env.getTimezone(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("天气接口返回 {}：{}", resp.statusCode(), truncate(resp.body()));
                return null;
            }
            JsonNode cur = json.readTree(resp.body()).path("current");
            Double temp = cur.has("temperature_2m") ? cur.get("temperature_2m").asDouble() : null;
            Integer hum = cur.has("relative_humidity_2m") ? cur.get("relative_humidity_2m").asInt() : null;
            Double precip = cur.has("precipitation") ? cur.get("precipitation").asDouble() : 0d;
            String time = cur.path("time").asText(null);

            return new WeatherVO(
                    env.getCity(),
                    temp,
                    hum,
                    precip,
                    time == null ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                 : time.replace('T', ' '),
                    "OPEN_METEO",
                    null,
                    impact(temp, hum, Boolean.TRUE.equals(toBool(env.getHasAirConditioner()))),
                    true);
        } catch (Exception e) {
            log.warn("获取天气失败：{}", e.toString());
            return null;
        }
    }

    private static Boolean toBool(Integer v) {
        return v != null && v == 1;
    }

    /**
     * 算出天气对养护的影响系数。
     *
     * <p>与 CarePlanService 的系数表保持一致（docs/02 3.4 节），
     * 但这里只做**展示**，不写进计划 —— 计划生成时才读环境。
     * 拆开是为了让用户能先看到"当前天气会把间隔改成多少"，再决定要不要重算。
     */
    private WeatherVO.Impact impact(Double temp, Integer humidity, boolean ac) {
        double tempCoef = 1.0;
        String tempNote = null;
        if (temp != null) {
            if (temp > 30) {
                tempCoef = 0.85;
                tempNote = String.format("气温 %.1f°C 高于 30°C", temp);
            } else if (temp < 10) {
                tempCoef = 1.3;
                tempNote = String.format("气温 %.1f°C 低于 10°C", temp);
            }
        }
        double humCoef = 1.0;
        String humNote = null;
        if (humidity != null) {
            if (humidity < 30) {
                humCoef = 0.9;
                humNote = String.format("空气湿度 %d%% 偏低", humidity);
            } else if (humidity > 60) {
                humCoef = 1.15;
                humNote = String.format("空气湿度 %d%% 偏高", humidity);
            }
        }

        StringBuilder note = new StringBuilder();
        if (tempNote != null) note.append(tempNote);
        if (humNote != null) {
            if (note.length() > 0) note.append("，");
            note.append(humNote);
        }
        if (note.length() == 0) {
            note.append(temp == null && humidity == null
                    ? "天气数据暂不可用，按物种基准间隔执行"
                    : "当前温湿度处于适宜区间，按物种基准间隔执行");
        }
        if (ac) {
            note.append("；室内开空调会使空气偏干，实际浇水间隔可略缩短");
        }
        return new WeatherVO.Impact(round2(tempCoef), round2(humCoef), note.toString());
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
