package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.service.CareTaskService;
import com.greenthumb.vo.CareLogPage;
import com.greenthumb.vo.CareLogVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 养护记录。路径与 frontend/src/api/careLog.js 对应：
 *   GET    /api/v1/care-logs        ?userPlantId&logType&limit
 *   POST   /api/v1/care-logs
 *   PUT    /api/v1/care-logs/{id}
 *   DELETE /api/v1/care-logs/{id}
 *
 * <p>实现放在 CareTaskService：任务完成与日志写入是同一个事务，
 * 拆成两个 service 会绕远路。
 */
@RestController
@RequestMapping("/api/v1/care-logs")
public class CareLogController {

    private final CareTaskService careService;

    public CareLogController(CareTaskService careService) {
        this.careService = careService;
    }

    @GetMapping
    public ApiResponse<CareLogPage> list(@RequestParam(required = false) Long userPlantId,
                                             @RequestParam(required = false) String logType,
                                             @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(careService.logs(CurrentUser.id(), userPlantId, logType, limit));
    }

    @PostMapping
    public ApiResponse<CareLogVO> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(careService.createLog(CurrentUser.id(), body));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<CareLogVO> update(@PathVariable("id") Long id,
                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(careService.updateLog(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        careService.deleteLog(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}
