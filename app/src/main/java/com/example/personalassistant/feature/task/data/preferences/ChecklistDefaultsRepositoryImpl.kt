package com.example.personalassistant.feature.task.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personalassistant.feature.task.domain.model.ChecklistDefaults
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.repository.ChecklistDefaultsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 22.5：新建清单项的默认参数（DataStore）
 *
 * 与其它偏好仓库共用同一个 `DataStore<Preferences>` 实例（由 CoreModule 注入），
 * 自己管自己的 key，互不干扰。
 */
@Singleton
class ChecklistDefaultsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ChecklistDefaultsRepository {

    override fun observe(): Flow<ChecklistDefaults> =
        dataStore.data.map { prefs ->
            ChecklistDefaults(
                defaultListId = prefs[KEY_DEFAULT_LIST_ID],
                defaultPriority = prefs[KEY_DEFAULT_PRIORITY]
                    ?.let { name -> TaskPriority.entries.firstOrNull { it.name == name } }
                    ?: TaskPriority.MEDIUM,
                defaultReminderMinutesBefore = prefs[KEY_DEFAULT_REMINDER_MINUTES],
            )
        }

    override suspend fun set(defaults: ChecklistDefaults) {
        dataStore.edit { prefs ->
            val listId = defaults.defaultListId
            if (listId == null) {
                prefs.remove(KEY_DEFAULT_LIST_ID)
            } else {
                prefs[KEY_DEFAULT_LIST_ID] = listId
            }

            prefs[KEY_DEFAULT_PRIORITY] = defaults.defaultPriority.name

            val reminder = defaults.defaultReminderMinutesBefore
            if (reminder == null) {
                prefs.remove(KEY_DEFAULT_REMINDER_MINUTES)
            } else {
                prefs[KEY_DEFAULT_REMINDER_MINUTES] = reminder
            }
        }
    }

    companion object {
        private val KEY_DEFAULT_LIST_ID = longPreferencesKey("checklist_default_list_id")
        private val KEY_DEFAULT_PRIORITY = stringPreferencesKey("checklist_default_priority")
        private val KEY_DEFAULT_REMINDER_MINUTES = intPreferencesKey("checklist_default_reminder_minutes")
    }
}
