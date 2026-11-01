package com.example.personalassistant.core.holiday.di

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personalassistant.core.holiday.scheduler.HolidayEveningReminderWorker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 7：节假日模块 Hilt 绑定
 *
 * 提供：
 * - [HolidayReminderScheduler]：周期性提前通知 Worker 调度入口
 * - 首次调度时间由用户设置决定（默认 21:00）；通过 initialDelay 实现
 */
@Module
@InstallIn(SingletonComponent::class)
object HolidayModule {

    /** 提前通知 Worker 唯一名 */
    internal const val WORK_NAME_HOLIDAY_EVENING = "cyrene-holiday-evening-reminder"

    @Provides
    @Singleton
    fun provideHolidayReminderScheduler(
        @ApplicationContext context: Context,
    ): HolidayReminderScheduler = HolidayReminderSchedulerImpl(context)
}

/**
 * 抽象绑定（@Binds 必须用 abstract class，不能用 object）
 *
 * Phase 8 Sub-step 7：把 HolidayRepository / HolidaySyncGateway 接口
 * 绑定到具体实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HolidayBindingModule {

    @Binds
    @Singleton
    abstract fun bindHolidayRepository(impl: com.example.personalassistant.core.holiday.data.HolidayRepositoryImpl):
            com.example.personalassistant.core.holiday.data.HolidayRepository

    @Binds
    @Singleton
    abstract fun bindHolidaySyncGateway(impl: com.example.personalassistant.core.holiday.data.StubHolidaySyncGateway):
            com.example.personalassistant.core.holiday.data.HolidaySyncGateway

    /** IsHolidayUseCase 接口 → IsHolidayUseCaseImpl 实现 */
    @Binds
    @Singleton
    abstract fun bindIsHolidayUseCase(impl: com.example.personalassistant.core.holiday.usecase.IsHolidayUseCaseImpl):
            com.example.personalassistant.core.holiday.usecase.IsHolidayUseCase
}

/**
 * 节假日提前通知 Worker 调度接口
 */
interface HolidayReminderScheduler {
    /**
     * 调度每日提前通知
     *
     * @param hour 每天执行小时（0-23，默认 21）
     * @param minute 每天执行分钟（0-59，默认 0）
     */
    fun scheduleEveningReminder(hour: Int = 21, minute: Int = 0)

    /** 取消提前通知 */
    fun cancelEveningReminder()

    /** 当前是否已调度 */
    suspend fun isScheduled(): Boolean
}

@Singleton
class HolidayReminderSchedulerImpl @Inject constructor(
    private val context: Context,
) : HolidayReminderScheduler {

    override fun scheduleEveningReminder(hour: Int, minute: Int) {
        val now = java.time.LocalDateTime.now()
        var nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }
        val initialDelayMillis = java.time.Duration.between(now, nextRun).toMillis()

        val request = PeriodicWorkRequestBuilder<HolidayEveningReminderWorker>(
            repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HolidayModule.WORK_NAME_HOLIDAY_EVENING,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancelEveningReminder() {
        WorkManager.getInstance(context).cancelUniqueWork(HolidayModule.WORK_NAME_HOLIDAY_EVENING)
    }

    override suspend fun isScheduled(): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(HolidayModule.WORK_NAME_HOLIDAY_EVENING)
            .get()
        return workInfos.any { !it.state.isFinished }
    }
}