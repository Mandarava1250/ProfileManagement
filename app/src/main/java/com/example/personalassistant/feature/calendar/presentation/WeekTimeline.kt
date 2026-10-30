package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.timetable.domain.model.Course

/**
 * 一周的时间轴数据（Phase 14 周视图）
 *
 * 与旧 [com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell] 的区别：
 * 旧模型只带「这一天有几个事件」，画不出课表；
 * 这里带上**课程和事件的时段**，才能按 1 小时行轴把块放到正确的位置。
 *
 * @param weekStart 本周一 00:00 的毫秒时间戳
 * @param days 周一 ~ 周日，共 7 天
 */
data class WeekTimeline(
    val weekStart: Long = 0L,
    val days: List<WeekDayColumn> = emptyList(),
) {
    companion object {
        /** 一周 7 天 */
        const val DAY_COUNT = 7

        /** 一天的毫秒数 */
        const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}

/**
 * 周视图里的一列（一天）
 *
 * @param date 当天 00:00 的毫秒时间戳
 * @param dayOfWeek 1 = 周一 … 7 = 周日（与 [Course.dayOfWeek] 同一套口径）
 * @param courses 这天的课程
 * @param events 这天的事件
 */
data class WeekDayColumn(
    val date: Long = 0L,
    val dayOfWeek: Int = 1,
    val courses: List<Course> = emptyList(),
    val events: List<Event> = emptyList(),
)
