package com.example.personalassistant.feature.task.di

import com.example.personalassistant.feature.task.data.preferences.ChecklistDefaultsRepositoryImpl
import com.example.personalassistant.feature.task.data.preferences.TaskDisplayRepositoryImpl
import com.example.personalassistant.feature.task.domain.repository.ChecklistDefaultsRepository
import com.example.personalassistant.feature.task.domain.repository.TaskDisplayRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 任务显示偏好模块 Hilt 绑定（Phase 8b'）
 *
 * 沿用 ThemeModule / LunarModule 的现有风格：
 * - Repository 接口 → 实现 用 @Binds
 * - UseCase 用 @Inject constructor 直接注入，无需在这里绑定
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TaskDisplayModule {

    @Binds
    @Singleton
    abstract fun bindTaskDisplayRepository(impl: TaskDisplayRepositoryImpl): TaskDisplayRepository

    /**
     * Phase 22.5：新建清单项的默认参数。
     *
     * 跟显示偏好一样走 DataStore，所以放在同一个模块里。
     */
    @Binds
    @Singleton
    abstract fun bindChecklistDefaultsRepository(
        impl: ChecklistDefaultsRepositoryImpl,
    ): ChecklistDefaultsRepository
}