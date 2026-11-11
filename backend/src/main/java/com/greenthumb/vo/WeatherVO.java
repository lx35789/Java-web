package com.greenthumb.vo;

/**
 * 当前天气 + 对养护的影响。
 *
 * <p>字段名对齐 frontend/src/views/profile/environment.vue 与 dashboard：
 * city / temperature / relativeHumidity / source / observedAt / impactOnCare。
 *
 * @param available false 时前端只显示 message，不渲染温湿度行
 *                （没有城市 / 天气服务不可用属于正常情况，不该当报错弹窗）
 */
public record WeatherVO(
        String city,
        Double temperature,
        Integer relativeHumidity,
        Double precipitation,
        String observedAt,
        String source,
        String message,
        Impact impactOnCare,
        Boolean available
) {
    public record Impact(
            /** 温度对浇水间隔的系数（<1 缩短，>1 延长） */
            Double tempCoefficient,
            Double humidityCoefficient,
            String note
    ) {
    }

    /** 构造一个"不可用"的响应，而不是抛异常 */
    public static WeatherVO unavailable(String msg) {
        return new WeatherVO(null, null, null, null, null, null, msg, null, false);
    }
}
