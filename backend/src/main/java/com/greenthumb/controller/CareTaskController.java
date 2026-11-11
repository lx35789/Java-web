package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.service.CareTaskService;
import com.greenthumb.vo.CareCalendarVO;
import com.greenthumb.vo.CareTaskVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 养护任务（日历）。路径与 frontend/src/api/careTask.js 对应：
 *   GET  /api/v1/care-tasks                  ?status&userPlantId&from&to
 *   GET  /api/v1/care-tasks/calendar         ?month=yyyy-MM
 *   GET  /api/v1/care-tasks/today
 *   POST /api/v1/care-tasks/{id}/complete    { amount, unit, detail, photoId }
 *   POST /api/v1/care-tasks/{id}/skip
 *   POST /api/v1/care-tasks/{id}/snooze      { dueDate }
 */
@RestController
@RequestMapping("/api/v1/care-tasks")
public class CareTaskController {

    private final CareTaskService taskService;

    public CareTaskController(CareTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<List<CareTaskVO>> list(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long userPlantId,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        return ApiResponse.ok(taskService.list(CurrentUser.id(), status, userPlantId, from, to));
    }

    @GetMapping("/calendar")
    public ApiResponse<CareCalendarVO> calendar(@RequestParam(required = false) String month) {
        return ApiResponse.ok(taskService.calendar(CurrentUser.id(), month));
    }

    @GetMapping("/today")
    public ApiResponse<List<CareTaskVO>> today() {
        return ApiResponse.ok(taskService.today(CurrentUser.id()));
    }

    @PostMapping("/{id:\\d+}/complete")
    public ApiResponse<CareTaskVO> complete(@PathVariable("id") Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(taskService.complete(CurrentUser.id(), id, body));
    }

    @PostMapping("/{id:\\d+}/skip")
    public ApiResponse<CareTaskVO> skip(@PathVariable("id") Long id) {
        return ApiResponse.ok(taskService.skip(CurrentUser.id(), id));
    }

    @PostMapping("/{id:\\d+}/snooze")
    public ApiResponse<CareTaskVO> snooze(@PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> body) {
        Object d = body == null ? null : body.get("dueDate");
        return ApiResponse.ok(taskService.snooze(CurrentUser.id(), id, d == null ? null : String.valueOf(d)));
    }
}
