package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.service.CarePlanService;
import com.greenthumb.vo.CarePlanVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 养护计划。路径与 frontend/src/api/carePlan.js 对应：
 *   GET    /api/v1/care-plans               ?userPlantId
 *   GET    /api/v1/care-plans/{id}
 *   POST   /api/v1/care-plans/generate      { userPlantId }
 *   PUT    /api/v1/care-plans/{id}
 *   DELETE /api/v1/care-plans/{id}
 *
 * <p>路径变量统一限制为数字，避免 /care-plans/generate 被 {id} 抢先匹配。
 */
@RestController
@RequestMapping("/api/v1/care-plans")
public class CarePlanController {

    private final CarePlanService planService;

    public CarePlanController(CarePlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ApiResponse<List<CarePlanVO>> list(@RequestParam(required = false) Long userPlantId) {
        return ApiResponse.ok(planService.list(CurrentUser.id(), userPlantId));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<CarePlanVO> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(planService.get(CurrentUser.id(), id));
    }

    /** 生成（或重新生成）一盆植物的养护计划，返回本次生成的计划列表 */
    @PostMapping("/generate")
    public ApiResponse<List<CarePlanVO>> generate(@RequestBody Map<String, Object> body) {
        Object pid = body == null ? null : body.get("userPlantId");
        Long id = pid instanceof Number ? ((Number) pid).longValue() : null;
        if (id == null) {
            throw new com.greenthumb.common.BizException(
                    com.greenthumb.common.ErrorCode.PARAM_INVALID, "请指定 userPlantId");
        }
        return ApiResponse.ok(planService.generate(CurrentUser.id(), id));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<CarePlanVO> update(@PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(planService.update(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> disable(@PathVariable("id") Long id) {
        planService.disable(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}
