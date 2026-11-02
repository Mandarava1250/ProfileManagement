package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Phase 22.7：重建**全部**任务提醒调度
 *
 * ## 为什么需要它
 *
 * Android 的 AlarmManager 闹钟在**设备重启**后会被系统清空，
 * 应用更新（MY_PACKAGE_REPLACED）后也可能丢。
 *
 * 原先的做法是「BootReceiver 只 cancelAll，等用户打开 App 时由观察流自动 reschedule」——
 * 这有个致命前提：**用户得打开 App**。
 * 用户重启手机后一直没开 App，那条提醒就永远不会再响。
 *
 * 所以这里改成：BootReceiver 启动后**扫全表重建**。
 *
 * ## 为什么走 [ScheduleReminderUseCase] 而不是直接调 scheduler
 *
 * [ScheduleReminderUseCase] 里已经处理了节假日避让 —— 重启后重建要走同一套规则，
 * 否则「节假日不响」这个承诺在重启后就失效了。
 */
open class RebuildTaskRemindersUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val scheduleReminder: ScheduleReminderUseCase,
) {
    /**
     * 重建全部提醒。
     *
     * @return 实际重建的提醒条数（已跳过提醒时间在过去的 —— 那种提醒响也没意义）
     */
    open suspend operator fun invoke(now: Long = System.currentTimeMillis()): Int {
        val tasks = taskRepository.getTasksWithReminders()
        var count = 0
        for (task in tasks) {
            val triggerAt = task.reminderTime ?: continue
            // 提醒时间已经过去 —— 重排也没意义（响出来是过期信息）
            if (triggerAt <= now) continue
            scheduleReminder(task)
            count++
        }
        return count
    }
}
