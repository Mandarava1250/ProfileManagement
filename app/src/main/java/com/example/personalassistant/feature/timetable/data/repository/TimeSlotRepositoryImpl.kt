package com.example.personalassistant.feature.timetable.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personalassistant.feature.timetable.domain.model.Period
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule
import com.example.personalassistant.feature.timetable.domain.repository.TimeSlotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TimeSlotRepository] 的 DataStore 实现
 *
 * 存储策略：把整张 [TimeSlotSchedule] 序列化成 JSON 字符串，单 key 存。
 *  - 12 条 period 也就 200 字节左右，没必要为每节课单独 key
 *  - 改用 JSON 后，UI 编辑时整体 `setSchedule(...)`，原子写入更稳
 *
 * 容错：
 *  - 读出 JSON 解析失败 / Period init 校验失败 → 视为"未配过"，返回 [TimeSlotSchedule.default()]
 *  - 写入时用 [TimeSlotSchedule] 的 init 校验挡住非法值（重复、index 跳号、时间倒置）
 *
 * 与 `SettingsRepository` 共用同一个 `DataStore<Preferences>` 实例，由 `CoreModule` 注入。
 */
@Singleton
class TimeSlotRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TimeSlotRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        // 紧凑单行
        prettyPrint = false
    }

    override fun observeSchedule(): Flow<TimeSlotSchedule> =
        dataStore.data.map { prefs ->
            parseOrDefault(prefs[KEY_SCHEDULE_JSON])
        }

    override suspend fun saveSchedule(schedule: TimeSlotSchedule) {
        // TimeSlotSchedule 的 init 已经做完整校验，这里只需要序列化为 JSON
        val payload = json.encodeToString(PeriodList(schedule.periods.toDto()))
        dataStore.edit { prefs ->
            prefs[KEY_SCHEDULE_JSON] = payload
        }
    }

    override suspend fun resetToDefault() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SCHEDULE_JSON)
        }
    }

    /**
     * 解析 + 校验，任意环节失败都回退默认。
     *
     * 这样旧用户即使配过非法值、被新校验拒掉的版本升级后也不会崩。
     */
    private fun parseOrDefault(raw: String?): TimeSlotSchedule {
        if (raw.isNullOrBlank()) return TimeSlotSchedule.default()
        return runCatching {
            val list = json.decodeFromString<PeriodList>(raw)
            TimeSlotSchedule(list.items.map { it.toDomain() })
        }.getOrElse { TimeSlotSchedule.default() }
    }

    @Serializable
    private data class PeriodList(
        val items: List<PeriodDto>,
    )

    @Serializable
    private data class PeriodDto(
        val index: Int,
        val startMinutes: Int,
        val endMinutes: Int,
    ) {
        fun toDomain(): Period = Period(index = index, startMinutes = startMinutes, endMinutes = endMinutes)
    }

    private fun List<Period>.toDto(): List<PeriodDto> = map { PeriodDto(it.index, it.startMinutes, it.endMinutes) }

    /**
     * 测试辅助：一次性读当前值（不订阅流）。
     * 默认 visibility 让单测能直接验证 DataStore 内容。
     */
    suspend fun rawJson(): String? = dataStore.data.first()[KEY_SCHEDULE_JSON]

    companion object {
        /**
         * `timetable_time_slots` key（注意用 `timetable_` 前缀，避免与 backup / theme / task 撞 key）
         */
        internal val KEY_SCHEDULE_JSON = stringPreferencesKey("timetable_time_slots")
    }
}
