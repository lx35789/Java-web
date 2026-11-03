package com.greenthumb.common;

/**
 * 当前登录用户上下文。
 * 由 AuthInterceptor 在请求进入时写入，afterCompletion 时清理。
 */
public final class CurrentUser {

    public record Info(Long userId, String username, String role) {
    }

    private static final ThreadLocal<Info> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(Info info) {
        HOLDER.set(info);
    }

    public static Info get() {
        return HOLDER.get();
    }

    /** 取当前用户 id；拿不到就说明没登录 */
    public static Long id() {
        Info info = HOLDER.get();
        if (info == null || info.userId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return info.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
