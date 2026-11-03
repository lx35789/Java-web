package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.dto.RecognitionRequest;
import com.greenthumb.service.RecognitionService;
import com.greenthumb.vo.RecognitionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物种识别。路径与 frontend/src/api/recognition.js 对应：
 *   POST /api/v1/recognitions        { photoId }
 *   GET  /api/v1/recognitions/{id}
 * <p>
 * 路径变量限制为纯数字，否则 /recognitions/history 会被当成 id 匹配。
 */
@RestController
@RequestMapping("/api/v1/recognitions")
public class RecognitionController {

    private final RecognitionService recognitionService;

    public RecognitionController(RecognitionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    @PostMapping
    public ApiResponse<RecognitionVO> recognize(@RequestBody RecognitionRequest request) {
        return ApiResponse.ok(recognitionService.recognize(
                CurrentUser.id(), request == null ? null : request.photoId()));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<RecognitionVO> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(recognitionService.get(CurrentUser.id(), id));
    }
}
