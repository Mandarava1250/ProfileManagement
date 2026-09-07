package com.example.personalassistant.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设置仓库（DataStore 封装）
 *
 * 当前承载：
 * - 主题偏好已迁移至 [com.example.personalassistant.feature.settings.theme.ThemeRepository]（Phase 8 Sub-step 6）
 * - Phase 8 Sub-step 4：自动备份开关、备份目录 URI、上次备份时间
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // ========== Phase 8 Sub-step 4：备份偏好 ==========

    /** 自动备份开关（默认 false） */
    val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_BACKUP_ENABLED] ?: false
    }

    /** 用户选定的备份目录 SAF URI（默认空字符串） */
    val backupFolderUri: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_BACKUP_FOLDER_URI] ?: ""
    }

    /** 上次自动备份完成的时间戳（默认 0L 表示从未备份） */
    val lastBackupAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_BACKUP_AT] ?: 0L
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setBackupFolderUri(uri: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BACKUP_FOLDER_URI] = uri
        }
    }

    suspend fun setLastBackupAt(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKUP_AT] = timestamp
        }
    }

    companion object {
        val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val KEY_BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val KEY_LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }
}
