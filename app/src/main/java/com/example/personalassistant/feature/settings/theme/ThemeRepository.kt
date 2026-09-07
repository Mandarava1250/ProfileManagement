package com.example.personalassistant.feature.settings.theme

import kotlinx.coroutines.flow.Flow

/**
 * 主题偏好仓库（Phase 8 Sub-step 6）
 *
 * 暴露：
 * - [observe] 实时主题偏好（mode + dynamicColor 合并）
 * - [setMode] 修改主题模式
 * - [setDynamicColor] 修改动态色开关
 *
 * 实现层用 DataStore 持久化；接口与 Android 解耦，便于测试替换。
 */
interface ThemeRepository {

    /**
     * 实时主题偏好 Flow
     *
     * - 首次订阅立即发射当前值
     * - 任一字段变更都会发射新的 [ThemePreferences]
     */
    fun observe(): Flow<ThemePreferences>

    /**
     * 设置主题模式（持久化）
     *
     * @param mode 新主题模式，传 null 等价于 [ThemeMode.DEFAULT]
     */
    suspend fun setMode(mode: ThemeMode)

    /**
     * 设置 Android 12+ Material You 动态色开关（持久化）
     */
    suspend fun setDynamicColor(enabled: Boolean)
}
