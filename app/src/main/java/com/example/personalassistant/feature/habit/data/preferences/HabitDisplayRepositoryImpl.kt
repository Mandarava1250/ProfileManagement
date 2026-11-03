package com.example.personalassistant.feature.habit.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.personalassistant.feature.habit.domain.repository.HabitDisplayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [HabitDisplayRepository] 的 DataStore 实现
 *
 * 与其它 feature 共用同一个 `DataStore<Preferences>` 实例（由 CoreModule 提供），
 * key 加 `habit_` 前缀避免撞车。
 *
 * 目前只有一个 key ——「日历同屏显示」开关（见 [HabitDisplayRepository] 的说明）。
 */
@Singleton
class HabitDisplayRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : HabitDisplayRepository {

    override fun observeShowInCalendar(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_SHOW_IN_CALENDAR] ?: false }

    override suspend fun setShowInCalendar(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_IN_CALENDAR] = enabled
        }
    }

    companion object {
        /** `habit_show_in_calendar` key：习惯是否在日历里同屏显示 */
        internal val KEY_SHOW_IN_CALENDAR = booleanPreferencesKey("habit_show_in_calendar")
    }
}
