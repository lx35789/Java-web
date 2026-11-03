package com.greenthumb.common;

/**
 * 业务错误码 —— 必须与前端 frontend/src/utils/constants.js 里的 ERROR_CODES 一一对应。
 * 新增错误码时两边同时改。
 */
public enum ErrorCode {

    SUCCESS(0, "成功"),
    PARAM_INVALID(1001, "参数校验失败"),
    UNAUTHORIZED(1002, "未登录或登录已过期"),
    FORBIDDEN(1003, "无权限访问该资源"),
    NOT_FOUND(1004, "资源不存在"),
    USERNAME_EXISTS(2001, "用户名已存在"),
    LOGIN_FAILED(2002, "用户名或密码错误"),
    ACCOUNT_DISABLED(2003, "账号已被禁用"),
    IMAGE_UNSUPPORTED(3001, "图片格式不支持（仅 jpg/png，且不超过 10MB）"),
    RECOGNITION_UNAVAILABLE(3002, "识别服务暂时不可用"),
    RECOGNITION_QUOTA_EXCEEDED(3003, "今日 AI 识别次数已用完"),
    SYMPTOM_INSUFFICIENT(4001, "症状信息不足，无法判断"),
    RATE_LIMITED(5001, "操作过于频繁，请稍后再试"),
    INTERNAL_ERROR(9999, "服务内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
