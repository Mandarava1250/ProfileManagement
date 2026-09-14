package com.example.personalassistant.feature.task.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskViewMode
import com.example.personalassistant.feature.task.domain.repository.TaskDisplayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TaskDisplayRepository] 的 DataStore 实现（Phase 8b'）
 *
 * 存储：
 * - 5 个 stringPreferencesKey：`quadrant_alias_<name>`（P1/P2/P3/P4/UNSCHEDULED）
 *   - 与 [ThemeRepositoryImpl] 风格保持一致
 *   - key 加 `quadrant_alias_` 前缀 + quadrant.name 后缀，避免与其他 feature 冲突
 * - 1 个 stringPreferencesKey：`task_view_mode`（"ALL" / "BY_QUADRANT"）
 * - 1 个 stringPreferencesKey：`task_selected_quadrant`（TaskQuadrant.name）
 *
 * 与 [com.example.personalassistant.feature.settings.theme.ThemeRepositoryImpl]
 * 共用同一个 DataStore<Preferences> 实例（由 CoreModule providePreferencesDataStore 注入），
 * 不同仓库各自管理自己的 key，互不干扰。
 *
 * 防御性：
 * - 写入非法 enum.name 时，回退到默认行为（不抛异常）
 * - 缺失 alias 时用 enum.label 兜底（[TaskDisplayPreferences.DEFAULT_ALIASES]）
 */
@Singleton
class TaskDisplayRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TaskDisplayRepository {

    override fun observe(): Flow<TaskDisplayPreferences> {
        return dataStore.data.map { prefs ->
            // 读取 5 个 alias；缺失用 enum.label 兜底
            val aliases = TaskQuadrant.entries.associateWith { q ->
                prefs[aliasKey(q)] ?: q.label
            }
            // viewMode：缺失/非法 → ALL
            val viewMode = parseViewMode(prefs[KEY_VIEW_MODE])
            // selectedQuadrant：缺失/非法 → null
            val selected = parseSelected(prefs[KEY_SELECTED_QUADRANT], viewMode)

            TaskDisplayPreferences(
                aliases = aliases,
                viewMode = viewMode,
                selectedQuadrant = selected,
            )
        }
    }

    override suspend fun setAlias(quadrant: TaskQuadrant, alias: String) {
        dataStore.edit { prefs ->
            prefs[aliasKey(quadrant)] = alias
        }
    }

    override suspend fun setViewMode(mode: TaskViewMode) {
        dataStore.edit { prefs ->
            prefs[KEY_VIEW_MODE] = mode.name
        }
    }

    override suspend fun selectQuadrant(quadrant: TaskQuadrant?) {
        dataStore.edit { prefs ->
            if (quadrant == null) {
                prefs.remove(KEY_SELECTED_QUADRANT)
            } else {
                prefs[KEY_SELECTED_QUADRANT] = quadrant.name
            }
        }
    }

    /**
     * 防御性解析：DataStore 里读到非 enum 名时回退到 ALL。
     *
     * 单测「写入非法 enum name 时回退到 default」覆盖这条路径。
     */
    private fun parseViewMode(raw: String?): TaskViewMode =
        raw?.let { name ->
            runCatching { TaskViewMode.valueOf(name) }.getOrNull()
        } ?: TaskViewMode.ALL

    /**
     * 防御性解析：[BY_QUADRANT] 模式 + 非法 quadrant 名称 → null（自动降级到 ALL 行为）。
     */
    private fun parseSelected(raw: String?, mode: TaskViewMode): TaskQuadrant? {
        if (mode != TaskViewMode.BY_QUADRANT) return null
        return raw?.let { name ->
            runCatching { TaskQuadrant.valueOf(name) }.getOrNull()
        }
    }

    companion object {
        /**
         * `task_view_mode` key（注意用 `task_` 前缀，避免与 theme 的 `theme_mode` 撞 key）
         */
        internal val KEY_VIEW_MODE = stringPreferencesKey("task_view_mode")

        /**
         * `task_selected_quadrant` key：当前 Tab 选中的象限（BY_QUADRANT 模式下生效）
         */
        internal val KEY_SELECTED_QUADRANT = stringPreferencesKey("task_selected_quadrant")

        /**
         * 给指定象限生成 alias key（供仓库实现内部与测试读写）。
         *
         * 公开 visibility 主要是为了让单测能够绕过 Repo API，直接验证 DataStore 层的
         * 解析逻辑（避免多次写入的 race）。
         */
        internal fun aliasKey(q: TaskQuadrant) = stringPreferencesKey("quadrant_alias_${q.name}")

        /**
         * 测试辅助：读取当前 DataStore 内的 viewMode 字符串（不暴露给业务层）。
         */
        suspend fun rawViewMode(dataStore: DataStore<Preferences>): String? =
            dataStore.data.first()[KEY_VIEW_MODE]
    }
}