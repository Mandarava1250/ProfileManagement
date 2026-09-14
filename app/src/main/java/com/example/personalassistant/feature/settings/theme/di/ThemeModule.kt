package com.example.personalassistant.feature.settings.theme.di

import com.example.personalassistant.feature.settings.theme.ThemeRepository
import com.example.personalassistant.feature.settings.theme.ThemeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 主题模块 Hilt 绑定（Phase 8 Sub-step 6）
 *
 * - UseCase 用 @Inject constructor 直接注入，无需在这里绑定
 * - Repository 接口 → 实现 用 @Binds
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeModule {

    @Binds
    @Singleton
    abstract fun bindThemeRepository(impl: ThemeRepositoryImpl): ThemeRepository
}
