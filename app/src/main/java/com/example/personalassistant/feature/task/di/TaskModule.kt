package com.example.personalassistant.feature.task.di

import com.example.personalassistant.feature.task.data.reminder.FakeReminderScheduler
import com.example.personalassistant.feature.task.data.repository.TaskRepositoryImpl
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 任务模块的 Hilt 绑定
 *
 * - TaskRepositoryImpl 已通过 @Inject constructor + @Singleton 自标注，
 *   这里只需把接口绑到实现。
 * - 所有 UseCase 都有 @Inject constructor，无需 @Provides。
 * - ReminderScheduler：Phase 4a 绑定 FakeReminderScheduler；Phase 5 替换为 WorkManager 实现即可
 *   （不改任何业务代码）
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TaskModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: FakeReminderScheduler): ReminderScheduler
}
