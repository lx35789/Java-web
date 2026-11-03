package com.greenthumb.common;

/**
 * 统一响应体：{ code, message, data, traceId }
 * <p>
 * 前端 frontend/src/api/request.js 的约定：
 * code == 0    -> 成功，前端直接拿到 data
 * code == 1002 -> 未登录/过期，前端会跳登录页
 * 其它         -> 前端按 ERROR_CODES 展示 message
 */
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public ApiResponse() {
        this.traceId = TraceContext.getOrCreate();
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = TraceContext.getOrCreate();
    }

    /* ---------------- 成功 ---------------- */

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /* ---------------- 失败 ---------------- */

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /* ---------------- getter / setter ---------------- */

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
