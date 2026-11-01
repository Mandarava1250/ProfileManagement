package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.di.CalendarModule
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import javax.inject.Inject
import javax.inject.Named

/**
 * Phase 8 Sub-step 1：取消一条日历事件的提醒。
 *
 * ## 触发时机
 * - [DeleteEventUseCase] 删除事件时
 * - [UpdateEventUseCase] 检测 reminder 由「非空」变「空」时
 * - 用户在编辑页把 reminderMinutes 设为 null 时
 *
 * ## 幂等性
 * - scheduler.cancel(eventId) 对不存在的 id 静默 no-op
 *
 * ## 注入细节
 * 用 `@Named(CalendarModule.NAMED_EVENT)` 选择 Event 端专用绑定。
 */
open class CancelEventReminderUseCase @Inject constructor(
    @Named(CalendarModule.NAMED_EVENT) private val scheduler: ReminderScheduler,
) {
    open suspend operator fun invoke(eventId: Long) {
        require(eventId > 0L) { "取消提醒的事件 id 必须大于 0" }
        scheduler.cancel(eventId)
    }
}