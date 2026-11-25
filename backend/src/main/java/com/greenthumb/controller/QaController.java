package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.service.QaService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 养护顾问（千问）。
 *
 * <pre>
 *   GET    /api/v1/qa/sessions                历史会话
 *   POST   /api/v1/qa/sessions                新建会话（可带 userPlantId / diagnosisContext）
 *   GET    /api/v1/qa/sessions/{id}           会话详情 + 全部消息
 *   PUT    /api/v1/qa/sessions/{id}           改标题 / 换植株 / 更新诊断上下文
 *   DELETE /api/v1/qa/sessions/{id}           软删除
 *   POST   /api/v1/qa/sessions/{id}/messages  提问，返回 text/event-stream
 *   POST   /api/v1/qa/messages/{id}/feedback  有用 / 没用
 * </pre>
 *
 * <p>和 /diagnoses 的区别：诊断是「一次性判因」，本接口是「围绕一盆植物持续聊」，
 * 每次回答前都会重新读该植株的档案、浇水计划、健康曲线。
 */
@RestController
@RequestMapping("/api/v1/qa")
public class QaController {

    private final QaService service;

    public QaController(QaService service) {
        this.service = service;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions() {
        return ApiResponse.ok(service.sessions(CurrentUser.id()));
    }

    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.createSession(CurrentUser.id(), body == null ? Map.of() : body));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(CurrentUser.id(), id));
    }

    @PutMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateSession(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteSession(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    /** 流式回答。返回 SSE，不走统一 ApiResponse 包装。 */
    @PostMapping(value = "/sessions/{id}/messages", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter ask(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.ask(CurrentUser.id(), id, body);
    }

    @PostMapping("/messages/{id}/feedback")
    public ApiResponse<Void> feedback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object v = body == null ? null : body.get("feedback");
        Integer value = v instanceof Number n ? n.intValue() : null;
        service.feedback(CurrentUser.id(), id, value);
        return ApiResponse.ok(null);
    }
}
