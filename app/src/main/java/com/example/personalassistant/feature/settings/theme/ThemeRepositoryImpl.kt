package com.example.personalassistant.feature.settings.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ThemeRepository] DataStore 实现
 *
 * 存储：
 * - `theme_mode`：String（"light"/"dark"/"system"），与旧版本兼容
 * - `dynamic_color`：Boolean（新增，默认 true）
 *
 * 与 [com.example.personalassistant.feature.settings.SettingsRepository]
 * 共用同一个 DataStore<Preferences> 实例（由 CoreModule providePreferencesDataStore 注入），
 * 不同仓库各自管理自己的 key，互不干扰。
 */
@Singleton
class ThemeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ThemeRepository {

    override fun observe(): Flow<ThemePreferences> {
        return combine(
            dataStore.data.map { prefs -> ThemeMode.fromKey(prefs[KEY_THEME_MODE]) },
            dataStore.data.map { prefs -> prefs[KEY_DYNAMIC_COLOR] ?: DEFAULT_DYNAMIC_COLOR },
        ) { mode, dynamicColor ->
            ThemePreferences(themeMode = mode, dynamicColor = dynamicColor)
        }
    }

    override suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.key
        }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    companion object {
        /**
         * 沿用旧版本的 key，避免升级后用户主题设置被重置
         */
        internal val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        /**
         * Phase 8 Sub-step 6 新增：Material You 动态色开关
         */
        internal val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        /**
         * 动态色默认值：false
         *
         * Phase 14 樱花粉 2.0 决策：Material You 壁纸取色会把品牌色「樱花粉」整个盖掉，
         * 导致同一套 UI 在不同设备上颜色完全不同。新装 / 从未设置过的用户默认走品牌色板，
         * 想用壁纸色的仍可在设置页打开开关。
         *
         * 注意：只影响「DataStore 里没有 dynamic_color 这一项」的安装。
         * 已经手动开过或关过的用户，存量值原样保留，不会被重置。
         */
        internal const val DEFAULT_DYNAMIC_COLOR = false
    }
}
