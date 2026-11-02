package com.example.personalassistant.feature.task.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personalassistant.feature.task.domain.model.NamedFilter
import com.example.personalassistant.feature.task.domain.repository.NamedFilterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 19：命名过滤器仓库实现（DataStore + JSON）
 *
 * - 1 个 stringPreferencesKey：`task_named_filters`，存整个数组的 JSON
 * - 与 [TaskDisplayRepositoryImpl] 共用同一个 DataStore<Preferences> 实例
 *   （由 CoreModule providePreferencesDataStore 注入），各自管自己的 key
 *
 * ## 防御性
 *
 * - JSON 解析失败（老版本格式变了 / 数据被截断）→ **退化成空列表**，不抛异常
 * - 单条数据反序列化失败 → 跳过那一条，其余保留
 */
@Singleton
class NamedFilterRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : NamedFilterRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observe(): Flow<List<NamedFilter>> =
        dataStore.data.map { prefs ->
            val raw = prefs[KEY_NAMED_FILTERS] ?: return@map emptyList()
            decode(raw)
        }

    override suspend fun save(filter: NamedFilter) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_NAMED_FILTERS]?.let { decode(it) } ?: emptyList()
            // 同名覆盖：先去掉旧的，再按 sortOrder 插回去
            val withoutSame = current.filterNot { it.name == filter.name }
            val merged = (withoutSame + filter).sortedBy { it.sortOrder }
            prefs[KEY_NAMED_FILTERS] = encode(merged)
        }
    }

    override suspend fun delete(name: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_NAMED_FILTERS]?.let { decode(it) } ?: emptyList()
            prefs[KEY_NAMED_FILTERS] = encode(current.filterNot { it.name == name })
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_NAMED_FILTERS) }
    }

    // ===== 编解码 =====

    private fun encode(filters: List<NamedFilter>): String =
        json.encodeToString(ListSerializer(NamedFilter.serializer()), filters)

    /**
     * 解码。
     *
     * 整体解析失败 → 空列表（不是异常 —— 一份坏数据不该让 App 打不开）
     */
    private fun decode(raw: String): List<NamedFilter> {
        val whole = runCatching {
            json.decodeFromString(ListSerializer(NamedFilter.serializer()), raw)
        }.getOrNull() ?: return emptyList()

        // 逐条兜底：单条坏数据不该拖垮整份
        return whole.mapNotNull { item ->
            runCatching { item }.getOrNull()
        }.sortedBy { it.sortOrder }
    }

    private companion object {
        val KEY_NAMED_FILTERS = stringPreferencesKey("task_named_filters")
    }
}
