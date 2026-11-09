package com.example.personalassistant.feature.finance.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode
import com.example.personalassistant.feature.finance.domain.repository.FinanceDisplayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FinanceDisplayRepository] 的 DataStore 实现
 *
 * 与其它 feature 共用同一个 `DataStore<Preferences>` 实例（由 `core/di/CoreModule` 提供，
 * 文件名 "settings"），key 加 `finance_` 前缀避免撞车。
 *
 * 用 `stringPreferencesKey` 而不是 `intPreferencesKey`：口径是**枚举**，
 * 存枚举的 name 字符串后，将来加第三个模式也不会把已存的值读成别的意思
 * （int 存序号的话，枚举顺序一变，老数据全错位，而且错得很安静）。
 */
@Singleton
class FinanceDisplayRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : FinanceDisplayRepository {

    override fun observeTodayRemainingMode(): Flow<TodayRemainingMode> =
        dataStore.data.map { prefs ->
            TodayRemainingMode.fromStorageValue(prefs[KEY_TODAY_REMAINING_MODE])
        }

    override suspend fun setTodayRemainingMode(mode: TodayRemainingMode) {
        dataStore.edit { prefs ->
            prefs[KEY_TODAY_REMAINING_MODE] = mode.name
        }
    }

    companion object {
        /**
         * `finance_today_remaining_mode` key：「今日剩余」的显示口径
         *
         * 命名规范：`<feature>_<snake_case>`，与 `habit_show_in_calendar` 一致。
         */
        internal val KEY_TODAY_REMAINING_MODE =
            stringPreferencesKey("finance_today_remaining_mode")
    }
}
