package com.example.personalassistant.feature.habit.di

import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.feature.habit.data.local.HabitCheckInDao
import com.example.personalassistant.feature.habit.data.local.HabitDao
import com.example.personalassistant.feature.habit.data.preferences.HabitDisplayRepositoryImpl
import com.example.personalassistant.feature.habit.data.repository.HabitRepositoryImpl
import com.example.personalassistant.feature.habit.domain.repository.HabitDisplayRepository
import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 23：习惯模块的 Hilt 绑定
 *
 * - HabitRepositoryImpl / HabitDisplayRepositoryImpl 已通过 @Inject constructor + @Singleton 自标注，
 *   这里只需把接口绑到实现
 * - 两个 DAO 的 @Provides 放在本模块的 companion 里 —— 习惯是自闭环模块，
 *   不必再去动 CoreModule（那边是全局 DAO 清单，改它容易和别的分支撞车）
 * - 所有 UseCase 都有 @Inject constructor，无需 @Provides
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HabitModule {

    @Binds
    @Singleton
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds
    @Singleton
    abstract fun bindHabitDisplayRepository(impl: HabitDisplayRepositoryImpl): HabitDisplayRepository

    companion object {

        @Provides
        fun provideHabitDao(db: AppDatabase): HabitDao = db.habitDao()

        @Provides
        fun provideHabitCheckInDao(db: AppDatabase): HabitCheckInDao = db.habitCheckInDao()
    }
}
