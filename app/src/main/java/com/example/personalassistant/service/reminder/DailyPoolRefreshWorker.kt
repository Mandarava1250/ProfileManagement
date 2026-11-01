package com.example.personalassistant.service.reminder

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personalassistant.feature.finance.domain.usecase.RefreshDailyPoolUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Phase 7a：每日 00:01 触发刷新的 Worker
 *
 * 调度链路：
 * - Application.onCreate()：调度器注册"下一个 00:01"的 Worker
 * - BootReceiver.onReceive()：设备重启后重新调度
 * - Worker 自身跑完后：调用 [DailyPoolRefreshScheduler.scheduleNext] 续约下一天
 *
 * 入参：无（WorkManager 只用 unique name 区分）
 */
@HiltWorker
class DailyPoolRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val refreshDailyPool: RefreshDailyPoolUseCase,
    private val scheduler: DailyPoolRefreshScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        Log.i(TAG, "🟢 [DailyPoolRefreshWorker] start at ${System.currentTimeMillis()}")
        refreshDailyPool(System.currentTimeMillis())
        // 自续约下一天
        scheduler.scheduleNext(System.currentTimeMillis())
        Log.i(TAG, "✅ [DailyPoolRefreshWorker] success")
        Result.success()
    } catch (e: Exception) {
        Log.e(TAG, "DailyPoolRefreshWorker failed", e)
        Result.retry() // WorkManager 内置 exponential backoff
    }

    companion object {
        private const val TAG = "DailyPoolRefresh"
    }
}
