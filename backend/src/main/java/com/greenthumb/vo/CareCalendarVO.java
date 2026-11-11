package com.greenthumb.vo;

import java.util.List;
import java.util.Map;

/**
 * 养护日历视图。
 *
 * <p>结构刻意与 frontend/src/mock/index.js 的 calendar 路由保持一致：
 * <pre>
 * { month, days: [{date, tasks}], summary: {total, pending, done, overdue} }
 * </pre>
 * 最初实现成 {@code Map<日期, 任务列表>}，前端拿不到 data.days，
 * 结果日历渲染出整月却「待完成 0」——空数组和缺失字段都不报错，只表现为没数据。
 *
 * @param days 只包含**有任务**的日期；前端自己在日历格子里按日期查表
 */
public record CareCalendarVO(
        String month,
        List<Day> days,
        Summary summary
) {
    public record Day(String date, List<CareTaskVO> tasks) {
    }

    public record Summary(int total, int pending, int done, int overdue) {
    }
}
