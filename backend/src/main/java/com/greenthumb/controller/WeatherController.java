package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.service.WeatherService;
import com.greenthumb.vo.WeatherVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 天气。路径与 frontend/src/api/weather.js 对应：
 *   GET  /api/v1/weather/current   当前天气（带 15 分钟缓存）
 *   POST /api/v1/weather/sync      强制同步
 *
 * <p>没有城市时 current 返回 available=false + message，不抛异常 ——
 * 这是正常状态，前端据此引导用户去设置，而不是弹红色报错。
 */
@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public ApiResponse<WeatherVO> current() {
        return ApiResponse.ok(weatherService.current(CurrentUser.id()));
    }

    @PostMapping("/sync")
    public ApiResponse<WeatherVO> sync() {
        return ApiResponse.ok(weatherService.sync(CurrentUser.id()));
    }
}
