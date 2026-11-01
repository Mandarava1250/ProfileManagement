package com.example.personalassistant.feature.calendar.di

import com.example.personalassistant.feature.calendar.data.repository.EventCategoryRepositoryImpl
import com.example.personalassistant.feature.calendar.domain.repository.EventCategoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 9：Event 分类仓库的 Hilt 绑定
 *
 * - EventCategoryRepositoryImpl 已通过 @Inject constructor + @Singleton 自标注
 * - 这里只需把接口绑到实现
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EventCategoryModule {

    @Binds
    @Singleton
    abstract fun bindEventCategoryRepository(impl: EventCategoryRepositoryImpl): EventCategoryRepository
}
