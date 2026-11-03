package com.greenthumb.common;

import java.util.UUID;

/**
 * 请求链路 ID 上下文。
 * 每个请求进来时由 TraceIdFilter 写入，响应体里的 traceId 就取自这里。
 */
public final class TraceContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static String getOrCreate() {
        String t = HOLDER.get();
        if (t == null || t.isEmpty()) {
            t = newId();
            HOLDER.set(t);
        }
        return t;
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
