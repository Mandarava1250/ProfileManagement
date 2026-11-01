package com.example.personalassistant.feature.settings.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主题 ViewModel（Phase 8 Sub-step 6）
 *
 * 用途：
 * - [MainActivity] 需要在 Composable 顶层观察主题偏好并应用到整个 App
 * - 直接 inject [ObserveThemePreferencesUseCase]（而不是 ThemeRepository），
 *   把 use case 层作为 ViewModel 依赖边界，未来方便换实现
 *
 * 与 [com.example.personalassistant.feature.settings.SettingsViewModel] 的分工：
 * - 本类只关心"读 + 主题"，轻量、永久在内存
 * - SettingsViewModel 处理备份/恢复等"写"动作，依赖更多
 */
@HiltViewModel
open class ThemeViewModel @Inject constructor(
    observeThemePreferences: ObserveThemePreferencesUseCase,
    private val repository: ThemeRepository,
) : ViewModel() {

    /**
     * 主题偏好 StateFlow（持久化的初始值 + DataStore Flow）
     *
     * - Eagerly 启动：App 启动后立即订阅，避免初次绘制时短暂 fallback 到默认
     * - viewModelScope：随 ViewModel 生命周期释放
     */
    val themePreferences: StateFlow<ThemePreferences> = observeThemePreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemePreferences(),
        )

    /**
     * Phase 14：改主题模式（浅色 / 深色 / 跟随系统）
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setMode(mode) }
    }

    /**
     * Phase 14：改主色。传 null 恢复品牌默认蓝。
     */
    fun setPrimaryColor(color: Int?) {
        viewModelScope.launch { repository.setPrimaryColor(color) }
    }

    /**
     * Phase 14：改背景风格
     */
    fun setBackgroundStyle(style: BackgroundStyle) {
        viewModelScope.launch { repository.setBackgroundStyle(style) }
    }

    /**
     * Phase 14：Material You 动态色开关
     */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }
}
