package com.example.personalassistant.core.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户权限询问状态仓库 —— Phase 8 末尾补完。
 *
 * - 用 DataStore Preferences 持久化"是否已询问过通知权限"
 * - 询问过即使用户拒绝也记为 true（不再触发系统弹窗）
 * - 用户要重新询问需手动清空偏好，或调用 [resetNotificationPermissionAsked]
 *
 * 与 SettingsRepository 同款模式：Flow 暴露 + suspend 写入。
 */
@Singleton
class PermissionRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * 是否已经询问过通知权限。
     *
     * - `false`（默认）→ 应用启动时仍可触发系统弹窗
     * - `true` → 已询问过（不管用户同意还是拒绝），不再自动弹窗
     */
    val notificationPermissionAsked: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATION_PERMISSION_ASKED] ?: false
    }

    /** 标记"已询问过通知权限"。 */
    suspend fun setNotificationPermissionAsked(asked: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_PERMISSION_ASKED] = asked
        }
    }

    companion object {
        val KEY_NOTIFICATION_PERMISSION_ASKED =
            booleanPreferencesKey("notification_permission_asked")
    }
}