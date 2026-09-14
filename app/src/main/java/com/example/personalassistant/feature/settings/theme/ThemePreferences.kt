package com.example.personalassistant.feature.settings.theme

/**
 * 主题偏好快照（Phase 8 Sub-step 6）
 *
 * 把 [themeMode]（裸 String）和 [dynamicColor]（未持久化参数）
 * 合并成一个不可变状态对象，便于：
 * 1. UI 一次 collect 拿到全部主题相关字段（避免多 Flow 同步竞态）
 * 2. 主题切换时 dynamicColor 一起回填默认值
 * 3. 后续扩展示色板/字体缩放时仅需在 data class 加字段
 *
 * 默认值：
 * - mode = SYSTEM：尊重用户系统设置
 * - dynamicColor = true：保持 Android 12+ Material You 行为不变
 */
data class ThemePreferences(
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val dynamicColor: Boolean = true,
)
