package com.example.personalassistant.feature.calendar.di

import com.example.personalassistant.feature.calendar.data.repository.CalendarRepositoryImpl
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository
}