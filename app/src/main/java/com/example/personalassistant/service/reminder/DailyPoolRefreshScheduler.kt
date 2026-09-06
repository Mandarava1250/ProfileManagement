package com.example.personalassistant.service.reminder

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7a：每日零钱池刷新的 WorkManager 调度器
 *
 * ## 设计要点
 * - 用 OneTimeWorkRequest + initialDelay 算到下一个 00:01 的毫秒数
 * - uniqueName = "daily-pool-refresh"，ExistingWorkPolicy.REPLACE 保证不重复入队
 * - Worker 跑完时由它自己调 [scheduleNext] 续约下一天（链式触发）
 * - 冷启动 / 设备重启也会调 [scheduleNext]（Application.onCreate / BootReceiver）
 *
 * ## 不要重复调度
 * - 多次调用 scheduleNext() 是安全的：REPLACE policy + uniqueName
 * - 但仍建议冷启动 / Worker 自续约时各调一次
 */
@Singleton
class DailyPoolRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    /**
     * 调度下一个 00:01 的刷新任务
     *
     * - 已存在同名任务会被 REPLACE 替换
     * - delay = (明日 00:01 - now) 毫秒数
     */
    fun scheduleNext(now: Long = System.currentTimeMillis()) {
        val zone = ZoneId.systemDefault()
        val nowDateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        val nextMidnight = if (nowDateTime.toLocalTime().isBefore(LocalTime.MIDNIGHT)) {
            nowDateTime.toLocalDate().atStartOfDay() // 今日 00:00（now < 00:00）
        } else {
            nowDateTime.toLocalDate().plusDays(1).atStartOfDay() // 明日 00:00
        }
        // 给 1 分钟 buffer：00:01 触发，避免边界 race
        val triggerAt = nextMidnight.plusMinutes(1)
        val delayMillis = Duration.between(nowDateTime, triggerAt).toMillis().coerceAtLeast(0L)

        Log.i(
            TAG,
            "scheduleNext: now=$nowDateTime, trigger=$triggerAt, delay=$delayMillis ms (${delayMillis / 1000}s)",
        )

        val request = OneTimeWorkRequestBuilder<DailyPoolRefreshWorker>()
            .setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** 取消已调度的任务（设置里关闭"每日零钱池刷新"等场景） */
    fun cancelNext() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        private const val TAG = "DailyPoolScheduler"
        const val UNIQUE_WORK_NAME = "daily-pool-refresh"
        const val WORK_TAG = "finance-daily-pool"
    }
}
