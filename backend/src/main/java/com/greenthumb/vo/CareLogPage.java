package com.greenthumb.vo;

import java.util.List;

/**
 * 养护记录分页返回。
 *
 * <p>结构对齐 frontend/src/views/care/logs.vue 的消费方式：
 * <pre>
 * const data = await getCareLogs(query)
 * list.value = data.list || []
 * total.value = data.total || 0
 * </pre>
 * 最初实现直接返回 {@code List<CareLogVO>}，前端拿不到 data.list，
 * 表格永远「暂无数据」——而接口本身是对的，排查时容易误判成后端没数据。
 *
 * @param total 满足筛选条件的总条数（不是本页条数）
 */
public record CareLogPage(List<CareLogVO> list, long total) {
}
