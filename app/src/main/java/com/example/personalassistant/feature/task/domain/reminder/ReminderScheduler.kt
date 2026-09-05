package com.example.personalassistant.feature.task.domain.reminder

/**
 * 提醒调度抽象 —— 业务层不关心底层是 WorkManager / AlarmManager / Push。
 *
 * Phase 4a: 默认实现 [FakeReminderScheduler] 仅 log 提示，不真正触发任何系统调度。
 * Phase 5: 新增 WorkManagerReminderScheduler / AlarmManagerReminderScheduler。
 */
interface ReminderScheduler {
    /** 调度一条提醒（已存在的会被覆盖 / cancel） */
    suspend fun schedule(info: ReminderInfo)

    /** 取消一条提醒 */
    suspend fun cancel(taskId: Long)

    /** 取消所有提醒（应用启动恢复 / 重置场景） */
    suspend fun cancelAll()
}
