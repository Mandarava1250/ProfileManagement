package com.example.personalassistant.feature.calendar.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.personalassistant.feature.calendar.domain.model.CalendarDisplayPreferences
import com.example.personalassistant.feature.calendar.domain.repository.CalendarDisplayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CalendarDisplayRepository] 的 DataStore 实现
 *
 * 与其它 feature 共用同一个 `DataStore<Preferences>` 实例（由 CoreModule 提供），
 * key 加 `calendar_` 前缀避免撞车。
 *
 * Phase 20.3：**不新增 Room 表** —— 显示偏好不值得占数据库的位置。
 */
@Singleton
class CalendarDisplayRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CalendarDisplayRepository {

    override fun observe(): Flow<CalendarDisplayPreferences> =
        dataStore.data.map { prefs ->
            CalendarDisplayPreferences(
                showCourses = prefs[KEY_SHOW_COURSES] ?: true,
                showTasks = prefs[KEY_SHOW_TASKS] ?: true,
                showCompleted = prefs[KEY_SHOW_COMPLETED] ?: true,
            )
        }

    override suspend fun setShowCourses(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_COURSES] = enabled
        }
    }

    override suspend fun setShowTasks(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_TASKS] = enabled
        }
    }

    override suspend fun setShowCompleted(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_COMPLETED] = enabled
        }
    }

    companion object {
        /** `calendar_show_courses` key：课程在日历里显不显示 */
        internal val KEY_SHOW_COURSES = booleanPreferencesKey("calendar_show_courses")

        /** `calendar_show_tasks` key：清单项在日历里显不显示 */
        internal val KEY_SHOW_TASKS = booleanPreferencesKey("calendar_show_tasks")

        /** `calendar_show_completed` key：已完成的清单项在日历里显不显示 */
        internal val KEY_SHOW_COMPLETED = booleanPreferencesKey("calendar_show_completed")
    }
}
