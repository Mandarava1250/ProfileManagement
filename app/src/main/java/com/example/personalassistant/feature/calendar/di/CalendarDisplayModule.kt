package com.example.personalassistant.feature.calendar.di

import com.example.personalassistant.feature.calendar.data.preferences.CalendarDisplayRepositoryImpl
import com.example.personalassistant.feature.calendar.domain.repository.CalendarDisplayRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 20.3：日历「显示设置」的 Hilt 绑定
 *
 * - CalendarDisplayRepositoryImpl 已通过 @Inject constructor + @Singleton 自标注，
 *   这里只需把接口绑到实现
 * - 习惯开关不在这里 —— 它复用习惯模块的 HabitDisplayRepository（HabitModule 里已绑）
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarDisplayModule {

    @Binds
    @Singleton
    abstract fun bindCalendarDisplayRepository(
        impl: CalendarDisplayRepositoryImpl,
    ): CalendarDisplayRepository
}
