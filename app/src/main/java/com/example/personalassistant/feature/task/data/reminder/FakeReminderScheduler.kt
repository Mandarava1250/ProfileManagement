package com.example.personalassistant.feature.task.data.reminder

import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4a Fake 实现：仅 log，不真正触发系统调度。
 *
 * 升级到 Phase 5 时换成 WorkManagerReminderScheduler，签名不变，DI 一行替换。
 */
@Singleton
class FakeReminderScheduler @Inject constructor() : ReminderScheduler {

    override suspend fun schedule(info: ReminderInfo) {
        Log.i(
            TAG,
            "🟦 [FakeReminder] schedule id=${info.taskId} title='${info.title}' at=${info.triggerAt}"
        )
    }

    override suspend fun cancel(taskId: Long) {
        Log.i(TAG, "🟦 [FakeReminder] cancel id=$taskId")
    }

    override suspend fun cancelAll() {
        Log.i(TAG, "🟦 [FakeReminder] cancelAll")
    }

    private companion object {
        const val TAG = "FakeReminder"
    }
}
