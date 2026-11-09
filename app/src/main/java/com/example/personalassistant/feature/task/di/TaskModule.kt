package com.example.personalassistant.feature.task.di

import com.example.personalassistant.feature.task.data.reminder.AlarmManagerReminderScheduler
import com.example.personalassistant.feature.task.data.reminder.CompositeReminderScheduler
import com.example.personalassistant.feature.task.data.reminder.CompositeReminderScheduler.Companion.NAMED_ALARM
import com.example.personalassistant.feature.task.data.reminder.CompositeReminderScheduler.Companion.NAMED_WORK
import com.example.personalassistant.feature.task.data.reminder.WorkManagerReminderScheduler
import com.example.personalassistant.feature.task.data.repository.ChecklistRepositoryImpl
import com.example.personalassistant.feature.task.data.repository.TagRepositoryImpl
import com.example.personalassistant.feature.task.data.repository.TaskListRepositoryImpl
import com.example.personalassistant.feature.task.data.repository.TaskRepositoryImpl
import com.example.personalassistant.feature.task.data.preferences.NamedFilterRepositoryImpl
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import com.example.personalassistant.feature.task.domain.repository.NamedFilterRepository
import com.example.personalassistant.feature.task.domain.repository.TagRepository
import com.example.personalassistant.feature.task.domain.repository.TaskListRepository
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import com.example.personalassistant.feature.task.presentation.util.DefaultTagPaletteSource
import com.example.personalassistant.feature.task.presentation.util.TagPaletteSource
import dagger.Binds
import dagger.Module
import dagger.Provides
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
 *
 * Phase 6c：新增 [DefaultTagPaletteSource] 单例提供（UI 选色板用）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TaskModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    /** Phase 15：清单仓库绑定。 */
    @Binds
    @Singleton
    abstract fun bindChecklistRepository(impl: ChecklistRepositoryImpl): ChecklistRepository

    /** Phase 17：清单（归属地）仓库绑定。 */
    @Binds
    @Singleton
    abstract fun bindTaskListRepository(impl: TaskListRepositoryImpl): TaskListRepository

    /** Phase 19：命名过滤器仓库绑定。 */
    @Binds
    @Singleton
    abstract fun bindNamedFilterRepository(impl: NamedFilterRepositoryImpl): NamedFilterRepository

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

    companion object {
        /**
         * Phase 6c：标签调色板默认 source（无 IO / 无状态，单例即可）。
         *
         * 用 @Provides + 伴生 object 让 abstract class 也能暴露 @Provides 函数（Hilt 编译期要求）。
         */
        @Provides
        @Singleton
        fun provideTagPaletteSource(): TagPaletteSource = DefaultTagPaletteSource()
    }
}