package com.example.personalassistant.feature.profile.di

import com.example.personalassistant.feature.profile.data.AccountRepository
import com.example.personalassistant.feature.profile.data.AccountRepositoryImpl
import com.example.personalassistant.feature.profile.data.CurrentAccountRepository
import com.example.personalassistant.feature.profile.data.CurrentAccountRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 3：profile（多账户）模块的 Hilt 绑定
 *
 * 两个实现都已用 @Inject constructor + @Singleton 自标注，这里只把接口绑上去
 * （与 [com.example.personalassistant.feature.finance.di.FinanceModule] 同款写法）。
 *
 * 注：DAO 不提供在这里 —— `UserProfileDao` 由
 * [com.example.personalassistant.core.di.CoreModule.provideUserProfileDao] 从 AppDatabase 取，
 * 与项目其余 10 个 DAO 的位置保持一致。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {

    @Binds
    @Singleton
    abstract fun bindCurrentAccountRepository(
        impl: CurrentAccountRepositoryImpl,
    ): CurrentAccountRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        impl: AccountRepositoryImpl,
    ): AccountRepository
}
