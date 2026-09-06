package com.example.personalassistant.feature.calendar.domain.model

/**
 * Phase 7c：日历视图类型
 *
 * 三种视图（与 ViewModel 配合切换）：
 * - MONTH：6×7 月视图，按日期显示事件/任务/课程 badge
 * - WEEK：横向 7 天 + 时间槽，重点看本周排课
 * - DAY：24 小时时间轴，重点看当日完整事件
 */
enum class CalendarViewType {
    MONTH,
    WEEK,
    DAY;

    companion object {
        val DEFAULT: CalendarViewType = MONTH
    }
}