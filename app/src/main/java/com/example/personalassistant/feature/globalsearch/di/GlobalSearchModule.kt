package com.example.personalassistant.feature.globalsearch.di

import com.example.personalassistant.feature.globalsearch.data.repository.GlobalSearchRepositoryImpl
import com.example.personalassistant.feature.globalsearch.domain.repository.GlobalSearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 3：GlobalSearch 模块的 Hilt 绑定
 *
 * - [GlobalSearchRepositoryImpl] 已通过 @Inject constructor + @Singleton 自标注
 * - 这里只需把接口绑到实现
 * - [GlobalSearchUseCase] 走 @Inject constructor，无需 @Provides
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GlobalSearchModule {

    @Binds
    @Singleton
    abstract fun bindGlobalSearchRepository(
        impl: GlobalSearchRepositoryImpl
    ): GlobalSearchRepository
}
