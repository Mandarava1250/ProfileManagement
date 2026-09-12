package com.example.personalassistant.feature.profile.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.personalassistant.feature.profile.data.local.AccountIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3：当前账户偏好实现（DataStore Preferences）
 *
 * 复用 [com.example.personalassistant.core.di.CoreModule.providePreferencesDataStore]
 * 提供的同一个 "settings" DataStore 实例（与 SettingsRepository / ThemeRepositoryImpl 同文件），
 * 避免多开 DataStore 实例导致「The Storage must be able to handle concurrent access」类冲突。
 *
 * 默认值 [AccountIds.DEFAULT_ACCOUNT_ID]：未设置过（首次安装 / 从 v8 升级上来）就是默认账户，
 * 与迁移把旧数据落在 id=1 的语义对齐。
 */
@Singleton
class CurrentAccountRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CurrentAccountRepository {

    override suspend fun currentAccountId(): Long =
        currentAccountIdFlow().first()

    override fun currentAccountIdFlow(): Flow<Long> =
        dataStore.data.map { prefs ->
            prefs[KEY_CURRENT_ACCOUNT_ID] ?: AccountIds.DEFAULT_ACCOUNT_ID
        }

    override suspend fun setCurrentAccountId(accountId: Long) {
        require(accountId > 0L) { "accountId must be positive, got $accountId" }
        dataStore.edit { prefs ->
            prefs[KEY_CURRENT_ACCOUNT_ID] = accountId
        }
    }

    companion object {
        val KEY_CURRENT_ACCOUNT_ID = longPreferencesKey("current_account_id")
    }
}
