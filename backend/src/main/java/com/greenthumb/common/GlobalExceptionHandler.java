package com.greenthumb.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * 保证任何情况下前端拿到的都是统一响应体，而不是 Spring 默认的错误页。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + defaultMessage(f))
                .collect(Collectors.joining("；"));
        return ApiResponse.fail(ErrorCode.PARAM_INVALID,
                detail.isEmpty() ? ErrorCode.PARAM_INVALID.getMessage() : detail);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ApiResponse<Void> handleBadRequest(Exception e) {
        log.warn("请求参数异常: {}", e.getMessage());
        return ApiResponse.fail(ErrorCode.PARAM_INVALID);
    }

    /**
     * 路径存在但 HTTP 方法不对（例如对只支持 GET 的接口发了 POST）。
     * 返回 405 而不是 500 —— 联调时一眼能看出是方法写错了。
     * 注意：前端的混合模式只在 404 时回退 Mock，405 会照常暴露，这是故意的。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {} 支持 {} ", e.getMethod(), e.getSupportedHttpMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(ErrorCode.NOT_FOUND,
                        "请求方法不支持：" + e.getMethod() + "，该接口只支持 " + e.getSupportedHttpMethods()));
    }

    /**
     * 路径不存在 —— 返回 404 + 统一响应体。
     * 前端拿到 404 会提示「接口不存在」，这正是联调阶段想要的效果。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        String path = e.getResourcePath();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ErrorCode.NOT_FOUND, "接口不存在：/" + path));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("未处理异常 traceId={}", TraceContext.get(), e);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR);
    }

    private static String defaultMessage(FieldError f) {
        return f.getDefaultMessage() == null ? "参数不合法" : f.getDefaultMessage();
    }
}
