package com.example.personalassistant.core.reset.di

import com.example.personalassistant.core.reset.AppDataReset
import com.example.personalassistant.core.reset.DataResetExecutor
import com.example.personalassistant.core.reset.ResetRepository
import com.example.personalassistant.core.reset.ResetRepositoryImpl
import com.example.personalassistant.service.reminder.DailyPoolRefresher
import com.example.personalassistant.service.reminder.DailyPoolRefreshScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 阶段 Phase 6：Reset 模块的 Hilt 绑定。
 *
 * - 把 [ResetRepositoryImpl] 绑成 [ResetRepository] 接口，供 SettingsViewModel 注入。
 * - 把 [AppDataReset] 绑成 [DataResetExecutor] 接口（fun interface，可 lambda 注入），
 *   让 [ResetRepositoryImpl] 通过窄接口拿数据清能力 —— 既避免 mock 第三方依赖，
 *   也让 JVM 单测可以用 lambda 直接注入 fake。
 * - 把 [DailyPoolRefreshScheduler] 绑成 [DailyPoolRefresher] 接口（同样为 JVM 测试让路）。
 *
 * 实现类的所有依赖（scheduler / DataStore / Room）都已经在其它模块里提供，
 * 这里不需要再 @Provides。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ResetModule {

    @Binds
    @Singleton
    abstract fun bindResetRepository(impl: ResetRepositoryImpl): ResetRepository

    @Binds
    @Singleton
    abstract fun bindDataResetExecutor(impl: AppDataReset): DataResetExecutor

    @Binds
    @Singleton
    abstract fun bindDailyPoolRefresher(impl: DailyPoolRefreshScheduler): DailyPoolRefresher
}