package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.service.PlantObservationService;
import com.greenthumb.vo.ObservationVO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 植物状态观测。对应 frontend/src/api/plantObservation.js：
 *   GET  /api/v1/my-plants/{id}/observations?days=90   曲线数据（点 + 摘要 + 拐点）
 *   POST /api/v1/my-plants/{id}/observations           记录一次快评
 *   GET  /api/v1/my-plants/{id}/observations/latest    最近一次
 *
 * <p>挂在 my-plants 下而不是单独开 /observations：观测天然属于某一盆植物，
 * 路径里带 plantId 能让归属校验在 REST 层就表达清楚。
 */
@RestController
@RequestMapping("/api/v1/my-plants/{plantId:\\d+}/observations")
public class PlantObservationController {

    private final PlantObservationService service;

    public PlantObservationController(PlantObservationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> series(@PathVariable("plantId") Long plantId,
                                                   @RequestParam(defaultValue = "90") int days) {
        return ApiResponse.ok(service.series(CurrentUser.id(), plantId, days));
    }

    @GetMapping("/latest")
    public ApiResponse<ObservationVO> latest(@PathVariable("plantId") Long plantId) {
        return ApiResponse.ok(service.latest(CurrentUser.id(), plantId));
    }

    @PostMapping
    public ApiResponse<ObservationVO> create(@PathVariable("plantId") Long plantId,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(CurrentUser.id(), plantId, body));
    }
}
