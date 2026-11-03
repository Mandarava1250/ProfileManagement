package com.example.personalassistant.feature.calendar.domain.util

import com.example.personalassistant.feature.task.domain.model.Task

/**
 * Phase 20.2：清单项「带时间段」的判定 —— **三个过滤点共用的唯一口径**
 *
 * ## 为什么要有它
 *
 * Phase 20.2 要求日历把带时间段的清单项渲染成时段块，
 * 而这件事要在**三个地方**各做一次：
 *
 * 1. [com.example.personalassistant.feature.calendar.data.repository.CalendarRepositoryImpl.observeDayDetail]
 *    —— 决定「这条清单项算不算这一天的内容」
 * 2. [com.example.personalassistant.feature.calendar.data.repository.CalendarRepositoryImpl.observeMonthSummary]
 *    —— 决定「这一天该记几个清单项」
 * 3. [com.example.personalassistant.feature.calendar.presentation.CalendarViewModel.observeWeekTimeline]
 *    —— 决定「周视图这一列怎么画它」
 *
 * 三处各写一遍，迟早会写歪（漏一处 → 月/周/日三个视图对不上）。
 * 所以口径全部收在这里，三处都调它。
 *
 * ## 口径
 *
 * - **带时段** = `startAt` 与 `endAt` **都有值**（只有一列有值当作没填）
 * - 带时段的清单项**按它占用的那几天**出现，而不是按 `dueDate`
 *   （跨午夜的会出现在两天里 —— 见 [CalendarTimeRange]）
 */
object CalendarTaskFilter {

    /** 是否是「带时间段」的清单项 */
    fun isTimed(task: Task): Boolean = task.startAt != null && task.endAt != null

    /**
     * 这个清单项占用了哪几天（当天 00:00 时间戳，升序）
     *
     * 没填时间段的清单项 → 空列表（它靠 dueDate 归属，不靠时段）
     */
    fun occupiedDays(task: Task): List<Long> {
        val start = task.startAt ?: return emptyList()
        val end = task.endAt ?: return emptyList()
        return CalendarTimeRange.segments(start, end).map { it.dayStart }
    }

    /** 这个清单项是否占用指定那一天（dayStart = 当天 00:00 时间戳） */
    fun occupiesDay(task: Task, dayStart: Long): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        // 归一化到那天的本地零点 —— 免得夏令时切换日（午夜不是整点）对不上
        val target = java.time.Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return occupiedDays(task).contains(target)
    }

    /**
     * 这个清单项在指定那一天上的那一截时段
     *
     * @return null = 没填时间段，或者不落在那一天
     */
    fun segmentOn(task: Task, dayStart: Long): CalendarTimeRange.Segment? {
        val start = task.startAt ?: return null
        val end = task.endAt ?: return null
        return CalendarTimeRange.segmentOn(start, end, dayStart)
    }
}
