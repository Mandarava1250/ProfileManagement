package com.example.personalassistant.feature.calendar.domain.model

/**
 * Phase 7c：月视图单日汇总（轻量版 DayDetail）
 *
 * 与 [DayDetail] 区别：不携带完整 Event/Task/Course 对象，
 * 只携带计数 + 是否存在标志，用于月视图 6×7 网格的 badge 渲染。
 *
 * 数据源与 [DayDetail] 完全一致（同一聚合查询的投影），但内存占用小得多。
 *
 * @param date 当天 00:00 时间戳
 * @param eventCount 当日事件数
 * @param taskCount 当日未完成任务数
 * @param courseCount 当日课程数
 * @param isToday 是否今天（便于 UI 高亮，无需再次判断）
 */
data class DaySummary(
    val date: Long,
    val eventCount: Int = 0,
    val taskCount: Int = 0,
    val courseCount: Int = 0,
    val isToday: Boolean = false,
) {
    val hasAny: Boolean get() = eventCount > 0 || taskCount > 0 || courseCount > 0
    val totalCount: Int get() = eventCount + taskCount + courseCount
}

/**
 * 月范围汇总返回类型：`Map<当天 00:00 时间戳, DaySummary>`
 *
 * 不在范围内的日期不出现在 Map 中；UI 用 `Map[date]` O(1) 取值。
 */
typealias MonthSummary = Map<Long, DaySummary>