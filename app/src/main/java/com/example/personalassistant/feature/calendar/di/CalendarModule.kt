package com.example.personalassistant.feature.calendar.di

import com.example.personalassistant.feature.calendar.data.reminder.EventAlarmManagerReminderScheduler
import com.example.personalassistant.feature.calendar.data.repository.CalendarRepositoryImpl
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Phase 7c：calendar 模块的 Hilt 绑定
 *
 * - CalendarRepositoryImpl 已通过 @Inject constructor + @Singleton 自标注，
 *   这里只需把接口绑到实现
 * - 所有 UseCase 都有 @Inject constructor，无需 @Provides
 *
 * 提前到 Sub-step 3 创建：日历 ViewModel 用了 @HiltViewModel，
 * 编译时需要完整的 Hilt component graph 才能生成 ViewModelProvider.Factory。
 *
 * ## Phase 8 Sub-step 1 新增：Event 端 ReminderScheduler 绑定
 * - 用 @Named("event") 区分 task 模块的默认 ReminderScheduler 绑定（走 Composite）
 * - Event 端只走 AlarmManager，不引入 WorkManager 分支（plan 决策 1）
 * - EventAlarmManagerReminderScheduler 的 REQUEST_CODE_BASE = 60_000，避免与任务段 50_000 撞码
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository

    /**
     * Event 端专用 ReminderScheduler 绑定（Phase 8 Sub-step 1）。
     *
     * UseCase 注入时用 `@Named("event")` 限定符选择此绑定。
     */
    @Binds
    @Singleton
    @Named(NAMED_EVENT)
    abstract fun bindEventReminderScheduler(
        impl: EventAlarmManagerReminderScheduler,
    ): ReminderScheduler

    companion object {
        const val NAMED_EVENT = "eventReminderScheduler"
    }
}