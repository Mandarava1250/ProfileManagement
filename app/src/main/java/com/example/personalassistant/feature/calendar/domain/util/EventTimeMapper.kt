package com.example.personalassistant.feature.calendar.domain.util

import com.example.personalassistant.feature.calendar.domain.model.Event
import java.time.Instant
import java.time.ZoneId

/**
 * Phase 8 Sub-step 9 Step 4：Event ↔ 精确时段切片
 *
 * ## 设计动机
 * [Event] 用 `Long?` 毫秒时间戳存 [startTime] / [endTime]，
 * [com.example.personalassistant.feature.timetable.domain.model.Course] 用 `Int` 当天分钟数 (0..1439) 存时段。
 *
 * 冲突检测需要在同一维度比较，因此引入 [TimeSlice] 作为中转：
 * - [dayOfWeek] 1=周一..7=周日（ISO 8601，与 [com.example.personalassistant.feature.timetable.data.local.CourseDao.findConflicts] 对齐）
 * - [startMinutes] / [endMinutes] 0..1439
 *
 * ## 时区
 * 用 [ZoneId.systemDefault]（与 CalendarRepositoryImpl.dateToDayOfWeek 对齐），
 * 避免跨时区用户的 dayOfWeek 计算偏移。
 *
 * ## 跨午夜
 * 不主动处理跨午夜事件（如 23:00→次日 1:00）。当前 Event 数据模型不支持，
 * 如未来需要应扩展为 `date + endDayOfWeek` 双字段。
 */
object EventTimeMapper {

    /** 时段切片（冲突检测的中转格式） */
    data class TimeSlice(
        val dayOfWeek: Int, // 1=周一, 7=周日 (ISO)
        val startMinutes: Int, // 0..1439
        val endMinutes: Int, // 0..1439，**允许 ≤ startMinutes**（整天事件情形由 isResult 为 null 处理；空时段调用方应自行跳过）
    )

    /**
     * 把 [Event] 转成 [TimeSlice]
     *
     * @return null 当且仅当 event 是整天事件（[Event.isEffectivelyAllDay] = true）
     *         —— 整天事件不参与精确时段冲突，UI 层通常不渲染冲突标
     */
    fun toSlice(event: Event): TimeSlice? {
        if (event.isEffectivelyAllDay) return null
        val start = event.startTime ?: return null
        val end = event.endTime ?: return null
        val zone = ZoneId.systemDefault()
        val startZdt = Instant.ofEpochMilli(start).atZone(zone)
        val endZdt = Instant.ofEpochMilli(end).atZone(zone)
        return TimeSlice(
            dayOfWeek = startZdt.dayOfWeek.value,
            startMinutes = startZdt.hour * 60 + startZdt.minute,
            endMinutes = endZdt.hour * 60 + endZdt.minute,
        )
    }
}