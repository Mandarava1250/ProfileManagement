package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.di.CalendarModule
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import javax.inject.Inject
import javax.inject.Named

/**
 * Phase 8 Sub-step 1：为一条日历事件调度提醒。
 *
 * ## 触发时机
 * - [AddEventUseCase] / [UpdateEventUseCase] 成功写入后，由 ViewModel 调本 UseCase
 *
 * ## 何时不调度
 * - `event.reminder == null` → 用户没设提醒，直接 return
 * - `event.startTime == null` → 全天事件，没有具体时刻，无法算触发时间
 *
 * ## triggerAt 计算
 * `event.startTime - event.reminder * 60_000ms`
 * 即在事件开始前 reminder 分钟触发。
 *
 * ## 注入细节
 * 用 `@Named(CalendarModule.NAMED_EVENT)` 选择 Event 端专用绑定，
 * 避免与 task 模块的默认 CompositeReminderScheduler 撞码。
 *
 * 设计取舍：
 * - 不在这里强制校验 `reminder > 0`（允许 0 = 立刻提醒，UI 端会屏蔽）
 * - 不在 UseCase 层处理 SecurityException（实现层已 try/catch）
 */
open class ScheduleEventReminderUseCase @Inject constructor(
    @Named(CalendarModule.NAMED_EVENT) private val scheduler: ReminderScheduler,
) {
    open suspend operator fun invoke(event: Event) {
        val reminder = event.reminder ?: return
        val startTime = event.startTime ?: return
        val triggerMillis = startTime - reminder * 60_000L
        scheduler.schedule(
            ReminderInfo(
                taskId = event.id,
                title = event.title,
                body = event.note.orEmpty(),
                triggerAt = java.time.Instant.ofEpochMilli(triggerMillis),
            )
        )
    }
}