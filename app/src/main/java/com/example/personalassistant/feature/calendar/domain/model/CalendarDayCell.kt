package com.example.personalassistant.feature.calendar.domain.model

/**
 * Phase 7c：月视图的单日格子（Cell）
 *
 * 描述某一具体日期的「事件 / 任务 / 课程」汇总信息，
 * 用于在 6×7 月视图的每个格子上显示角标 / 提示色。
 *
 * @param date 当天 00:00 时间戳（与 [Event.date] 同语义）
 * @param isCurrentMonth true 表示属于当前显示月份；false 表示上下月占位
 * @param isToday true 表示是系统今天
 * @param eventCount 当日 [Event] 数量
 * @param taskCount 当日 [com.example.personalassistant.feature.task.domain.model.Task] 未完成数
 * @param courseCount 当日 [com.example.personalassistant.feature.timetable.domain.model.Course] 数
 */
data class CalendarDayCell(
    val date: Long,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val eventCount: Int = 0,
    val taskCount: Int = 0,
    val courseCount: Int = 0,
    /**
     * Phase 24B：当日有打卡记录的习惯数
     */
    val habitCount: Int = 0,
) {
    /** 该日是否有任意内容（用于决定是否画 dot） */
    val hasAny: Boolean
        get() = eventCount > 0 || taskCount > 0 || courseCount > 0 || habitCount > 0

    /** 总计数（用于决定 badge 颜色优先级） */
    val totalCount: Int get() = eventCount + taskCount + courseCount + habitCount

    /** 是否有事件（用于单独画 event dot） */
    val hasEvents: Boolean get() = eventCount > 0

    /** 是否有任务 */
    val hasTasks: Boolean get() = taskCount > 0

    /** 是否有课程 */
    val hasCourses: Boolean get() = courseCount > 0

    /** Phase 24B：当天有没有习惯打卡 */
    val hasHabits: Boolean get() = habitCount > 0
}