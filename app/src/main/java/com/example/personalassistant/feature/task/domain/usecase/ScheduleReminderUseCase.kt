package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import javax.inject.Inject

/**
 * 为一条任务调度提醒。
 *
 * 调用时机：
 * - [UpsertTaskUseCase] 成功写入后，若 task.reminderTime 非空
 *
 * Phase 4a：[ReminderScheduler] 默认实现是 Fake（仅 log），所以这条 UseCase 现在是"声明意图"层；
 * Phase 5 真正接 WorkManager 时无需改这个 UseCase。
 */
open class ScheduleReminderUseCase @Inject constructor(
    private val scheduler: ReminderScheduler,
) {
    open suspend operator fun invoke(task: Task) {
        val triggerAt = task.reminderTime ?: return
        scheduler.schedule(
            ReminderInfo(
                taskId = task.id,
                title = task.title,
                body = task.description.orEmpty(),
                triggerAt = java.time.Instant.ofEpochMilli(triggerAt),
            )
        )
    }
}
