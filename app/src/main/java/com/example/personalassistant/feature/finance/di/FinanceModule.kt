package com.example.personalassistant.feature.finance.di

import com.example.personalassistant.feature.finance.data.repository.FinanceRepositoryImpl
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 7a：finance 模块的 Hilt 绑定
 *
 * - FinanceRepositoryImpl 已通过 @Inject constructor + @Singleton 自标注，
 *   这里只需把接口绑到实现
 * - 所有 UseCase 都有 @Inject constructor，无需 @Provides
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FinanceModule {

    @Binds
    @Singleton
    abstract fun bindFinanceRepository(impl: FinanceRepositoryImpl): FinanceRepository
}
