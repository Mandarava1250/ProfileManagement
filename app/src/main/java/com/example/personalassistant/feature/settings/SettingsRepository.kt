package com.example.personalassistant.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
 * - Phase 8 Sub-step 8：节假日避让总开关、提前通知开关/时间
 * - 记账月截止日（实现 [FinanceCutoffSettings]，让上层能拿接口去单测）
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : FinanceCutoffSettings {
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

    // ========== Phase 8 Sub-step 8：节假日 / 智能提醒偏好 ==========

    /**
     * 节假日避让总开关（默认 true）。
     *
     * - true  → 任务 reminder 链路遇到 HOLIDAY 时按各 Task 的 [com.example.personalassistant.core.holiday.model.HolidayStrategy] 决定跳过/顺延
     * - false → 节假日当天照常提醒（等价于 HolidayStrategy.KEEP_NORMAL 全局生效）
     *
     * 该开关由 SettingsViewModel 监听,不需要主动 push 到 reminder 链路。
     */
    val holidayAvoidEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HOLIDAY_AVOID_ENABLED] ?: true
    }

    /**
     * 每日提前通知总开关（默认 true）。
     *
     * - true  → HolidayEveningReminderWorker 调度
     * - false → Worker 取消,用户关闭后不再有提前通知
     */
    val holidayEveningReminderEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HOLIDAY_EVENING_ENABLED] ?: true
    }

    /** 提前通知时间 - 小时（0-23，默认 21） */
    val holidayEveningHour: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_HOLIDAY_EVENING_HOUR] ?: 21
    }

    /** 提前通知时间 - 分钟（0-59，默认 0） */
    val holidayEveningMinute: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_HOLIDAY_EVENING_MINUTE] ?: 0
    }

    suspend fun setHolidayAvoidEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HOLIDAY_AVOID_ENABLED] = enabled
        }
    }

    suspend fun setHolidayEveningReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HOLIDAY_EVENING_ENABLED] = enabled
        }
    }

    suspend fun setHolidayEveningTime(hour: Int, minute: Int) {
        require(hour in 0..23) { "hour must be 0..23, got $hour" }
        require(minute in 0..59) { "minute must be 0..59, got $minute" }
        dataStore.edit { prefs ->
            prefs[KEY_HOLIDAY_EVENING_HOUR] = hour
            prefs[KEY_HOLIDAY_EVENING_MINUTE] = minute
        }
    }

    // ========== 记账：月截止日 ==========

    /**
     * 记账账期的月截止日（默认 1 = 自然月）
     *
     * 这里的值是**原始值**，不做范围夹取 —— 夹取统一在
     * [com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy] 里做，
     * 避免 settings 层反向依赖 finance 域。
     */
    override val financeMonthCutoffDay: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_FINANCE_MONTH_CUTOFF_DAY] ?: DEFAULT_FINANCE_MONTH_CUTOFF_DAY
    }

    /**
     * 新截止日从哪个 ym 开始生效（默认 "1970-01" = 一直生效）
     *
     * 改了截止日之后，更早的账期区间保持不变，靠的就是这个字段。
     */
    override val financeMonthCutoffEffectiveFrom: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_FINANCE_MONTH_CUTOFF_EFFECTIVE_FROM] ?: DEFAULT_FINANCE_MONTH_CUTOFF_EFFECTIVE_FROM
    }

    /**
     * 写入月截止日
     *
     * @param day 截止日（1-28）
     * @param effectiveFromYm 新规则从哪个 ym 开始生效（"yyyy-MM"）
     */
    override suspend fun setFinanceMonthCutoff(day: Int, effectiveFromYm: String) {
        require(day in 1..28) { "month cutoff day must be 1..28, got $day" }
        dataStore.edit { prefs ->
            prefs[KEY_FINANCE_MONTH_CUTOFF_DAY] = day
            prefs[KEY_FINANCE_MONTH_CUTOFF_EFFECTIVE_FROM] = effectiveFromYm
        }
    }

    companion object {
        val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val KEY_BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val KEY_LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val KEY_HOLIDAY_AVOID_ENABLED = booleanPreferencesKey("holiday_avoid_enabled")
        val KEY_HOLIDAY_EVENING_ENABLED = booleanPreferencesKey("holiday_evening_enabled")
        val KEY_HOLIDAY_EVENING_HOUR = intPreferencesKey("holiday_evening_hour")
        val KEY_HOLIDAY_EVENING_MINUTE = intPreferencesKey("holiday_evening_minute")
        val KEY_FINANCE_MONTH_CUTOFF_DAY = intPreferencesKey("finance_month_cutoff_day")
        val KEY_FINANCE_MONTH_CUTOFF_EFFECTIVE_FROM =
            stringPreferencesKey("finance_month_cutoff_effective_from")

        /** 默认截止日 = 1 号（自然月，与本设置上线前行为一致） */
        const val DEFAULT_FINANCE_MONTH_CUTOFF_DAY = 1

        /** 默认生效 ym = 永远早于任何真实月份 */
        const val DEFAULT_FINANCE_MONTH_CUTOFF_EFFECTIVE_FROM = "1970-01"
    }
}
