package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import javax.inject.Inject

/**
 * 取消一条任务的提醒。
 *
 * 调用时机：
 * - [DeleteTaskUseCase] 删除任务时
 * - [UpsertTaskUseCase] 检测 reminderTime 由非空变空时
 */
open class CancelReminderUseCase @Inject constructor(
    private val scheduler: ReminderScheduler,
) {
    open suspend operator fun invoke(taskId: Long) {
        scheduler.cancel(taskId)
    }
}
