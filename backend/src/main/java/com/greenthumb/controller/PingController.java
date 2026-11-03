package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 连通性探针。
 * <p>
 * 验证链路：浏览器 -> Vite(5173) -> 代理 -> Spring Boot(8080) -> 统一响应体
 * 前端：GET /api/v1/ping
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "greenthumb-backend");
        data.put("status", "UP");
        data.put("time", LocalDateTime.now().format(FMT));
        data.put("java", System.getProperty("java.version"));
        data.put("os", System.getProperty("os.name"));
        return ApiResponse.ok(data);
    }
}
