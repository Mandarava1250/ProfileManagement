package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.core.holiday.usecase.IsHolidayUseCase
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 为一条任务调度提醒。
 *
 * 调用时机：
 * - [UpsertTaskUseCase] 成功写入后，若 task.reminderTime 非空
 *
 * Phase 4a：[ReminderScheduler] 默认实现是 Fake（仅 log），所以这条 UseCase 现在是"声明意图"层；
 * Phase 5 真正接 WorkManager 时无需改这个 UseCase。
 *
 * Phase 8 Sub-step 7：节假日避让。
 * - 调度前调用 [IsHolidayUseCase] 判断 reminderTime 落在哪一天
 * - 若该天是 HOLIDAY → 跳过本次调度（控制台日志说明）
 * - WorkManager / AlarmManager 会重新启动时 / 应用重启时由上游重新触发
 */
open class ScheduleReminderUseCase @Inject constructor(
    private val scheduler: ReminderScheduler,
    private val isHolidayUseCase: IsHolidayUseCase,
) {
    open suspend operator fun invoke(task: Task) {
        val triggerAt = task.reminderTime ?: return
        val triggerLocalDate = java.time.Instant.ofEpochMilli(triggerAt)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        if (isHolidayUseCase(triggerLocalDate)) {
            android.util.Log.i(
                TAG,
                "[HolidayAware] skip task=${task.id} trigger=${triggerLocalDate} (HOLIDAY)",
            )
            return
        }
        scheduler.schedule(
            ReminderInfo(
                taskId = task.id,
                title = task.title,
                body = task.description.orEmpty(),
                triggerAt = java.time.Instant.ofEpochMilli(triggerAt),
            )
        )
    }

    companion object {
        private const val TAG = "TaskReminder"
    }
}
