package com.example.personalassistant.feature.settings.di

import com.example.personalassistant.feature.settings.FinanceCutoffSettings
import com.example.personalassistant.feature.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 设置模块 Hilt 绑定
 *
 * - [FinanceCutoffSettings] 接口 → [SettingsRepository] 实现 用 @Binds
 *   （抽接口是为了让依赖它的 VM / UseCase / Repository 能用内存 fake 单测）
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindFinanceCutoffSettings(impl: SettingsRepository): FinanceCutoffSettings
}
