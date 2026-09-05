package com.example.personalassistant.feature.task.di

import com.example.personalassistant.feature.task.data.reminder.AlarmManagerReminderScheduler
import com.example.personalassistant.feature.task.data.reminder.CompositeReminderScheduler
import com.example.personalassistant.feature.task.data.reminder.CompositeReminderScheduler.Companion.NAMED_ALARM
import com.example.personalassistant.feature.task.data.reminder.CompositeReminderScheduler.Companion.NAMED_WORK
import com.example.personalassistant.feature.task.data.reminder.WorkManagerReminderScheduler
import com.example.personalassistant.feature.task.data.repository.TaskRepositoryImpl
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * 任务模块的 Hilt 绑定
 *
 * - TaskRepositoryImpl 已通过 @Inject constructor + @Singleton 自标注，
 *   这里只需把接口绑到实现。
 * - 所有 UseCase 都有 @Inject constructor，无需 @Provides。
 * - ReminderScheduler：
 *   - 默认 [ReminderScheduler] 接口绑定到 [CompositeReminderScheduler]（按延迟路由）
 *   - [NAMED_WORK] / [NAMED_ALARM] 分别绑到 WorkManager / AlarmManager 实现
 *     （供 [CompositeReminderScheduler] 内部按名注入，避免循环依赖）
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TaskModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: CompositeReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    @Named(NAMED_WORK)
    abstract fun bindWorkManagerScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    @Named(NAMED_ALARM)
    abstract fun bindAlarmManagerScheduler(impl: AlarmManagerReminderScheduler): ReminderScheduler
}
