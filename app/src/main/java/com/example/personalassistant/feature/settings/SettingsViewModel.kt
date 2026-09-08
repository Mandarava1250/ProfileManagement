package com.example.personalassistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.backup.di.BackupWorkerScheduler
import com.example.personalassistant.core.backup.exporter.BackupExporter
import com.example.personalassistant.core.backup.importer.BackupImporter
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.backup.serializer.BackupSerializer
import com.example.personalassistant.core.holiday.data.HolidayRepository
import com.example.personalassistant.core.holiday.di.HolidayReminderScheduler
import com.example.personalassistant.feature.settings.theme.ObserveThemePreferencesUseCase
import com.example.personalassistant.feature.settings.theme.SetDynamicColorUseCase
import com.example.personalassistant.feature.settings.theme.SetThemeModeUseCase
import com.example.personalassistant.feature.settings.theme.ThemeMode
import com.example.personalassistant.feature.settings.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Phase 8 Sub-step 4+6+8：备份 / 主题 / 节假日 设置 ViewModel
 *
 * 暴露：
 * - [themePreferences]：主题偏好（mode + dynamicColor，来自 ThemeRepository）
 * - [backupState]：备份偏好（autoBackup / folderUri / lastBackupAt）
 * - [holidayState]：节假日/智能提醒偏好（avoid / eveningEnabled / time / 数据范围 / 同步提示）
 * - [statusMessage]：操作反馈（成功/失败消息）
 * - [exportNow]：立即备份（用户给 OutputStream，由 ViewModel 写入 payload）
 * - [importNow]：立即导入（用户给 InputStream）
 * - [setAutoBackup]：切换自动备份（同时启用/禁用 Worker）
 * - [setBackupFolderUri]：更新目录 URI
 * - [setThemeMode] / [setDynamicColor]：主题偏好写操作
 * - [setHolidayAvoidEnabled] / [setHolidayEveningEnabled] / [setHolidayEveningTime]：节假日偏好
 * - [triggerHolidaySync]：占位同步入口（当前 Stub）
 * - [dismissHolidaySyncPrompt]：用户关闭「节假日数据即将过期」弹窗
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val serializer: BackupSerializer,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val backupWorkerScheduler: BackupWorkerScheduler,
    private val holidayReminderScheduler: HolidayReminderScheduler,
    private val holidayRepository: HolidayRepository,
    observeThemePreferences: ObserveThemePreferencesUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    private val setDynamicColorUseCase: SetDynamicColorUseCase,
) : ViewModel() {

    /** 主题偏好快照（mode + dynamicColor） */
    val themePreferences: StateFlow<ThemePreferences> = observeThemePreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemePreferences(),
        )

    /** 备份偏好快照 */
    val backupState: StateFlow<BackupPreferencesUi> = combineBackupFlows()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BackupPreferencesUi(),
        )

    /** Phase 8 Sub-step 8：节假日/智能提醒偏好快照 */
    val holidayState: StateFlow<HolidayPreferencesUi> = combineHolidayFlows()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HolidayPreferencesUi(),
        )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** Phase 8 Sub-step 8：网络授权同步提示（null = 不显示） */
    private val _syncPromptYear = MutableStateFlow<Int?>(null)
    val syncPromptYear: StateFlow<Int?> = _syncPromptYear.asStateFlow()

    init {
        // 启动时检测节假日数据是否需要联网同步（每年触发一次）
        viewModelScope.launch {
            val maxYear = withContext(Dispatchers.IO) { holidayRepository.getMaxYear() }
            val currentYear = LocalDate.now().year
            if (maxYear != null && currentYear > maxYear) {
                _syncPromptYear.value = currentYear
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { setThemeModeUseCase(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { setDynamicColorUseCase(enabled) }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoBackupEnabled(enabled)
            if (enabled) {
                backupWorkerScheduler.scheduleDaily()
            } else {
                backupWorkerScheduler.cancelDaily()
            }
        }
    }

    fun setBackupFolderUri(uri: String) {
        viewModelScope.launch { repository.setBackupFolderUri(uri) }
    }

    // ===== Phase 8 Sub-step 8：节假日/智能提醒 =====

    fun setHolidayAvoidEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHolidayAvoidEnabled(enabled) }
    }

    /**
     * 切换每日提前通知开关。
     *
     * - true  → 立即按当前 hour/minute 重新调度（[HolidayReminderScheduler] 内部 UPDATE）
     * - false → 取消 Worker,不再有提前通知
     */
    fun setHolidayEveningEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHolidayEveningReminderEnabled(enabled)
            val current = holidayState.value
            if (enabled) {
                holidayReminderScheduler.scheduleEveningReminder(
                    hour = current.eveningHour,
                    minute = current.eveningMinute,
                )
            } else {
                holidayReminderScheduler.cancelEveningReminder()
            }
        }
    }

    /**
     * 调整提前通知时间。开关没变也会重新调度,保证时间生效。
     */
    fun setHolidayEveningTime(hour: Int, minute: Int) {
        require(hour in 0..23) { "hour must be 0..23" }
        require(minute in 0..59) { "minute must be 0..59" }
        viewModelScope.launch {
            repository.setHolidayEveningTime(hour, minute)
            if (holidayState.value.eveningEnabled) {
                holidayReminderScheduler.scheduleEveningReminder(hour, minute)
            }
        }
    }

    /**
     * 占位同步：调 [HolidaySyncGateway] 触发（Stub 实现只 toast「网络同步暂未启用」）。
     *
     * Phase 8 Sub-step 8 范围:UI 入口已通,网络实现在未来 sub-step 接入。
     */
    fun triggerHolidaySync() {
        val year = _syncPromptYear.value ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { holidayRepository.syncHolidaysForYear(year) }
            _statusMessage.value = if (ok) "节假日数据已同步（$year）" else "同步失败,稍后重试"
            _syncPromptYear.value = null
        }
    }

    /** 用户关闭「节假日数据即将过期」弹窗 */
    fun dismissHolidaySyncPrompt() {
        _syncPromptYear.value = null
    }

    fun dismissStatusMessage() {
        _statusMessage.value = null
    }

    fun exportNow(outputStream: java.io.OutputStream, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val payloadResult = withContext(Dispatchers.IO) { serializer.snapshot() }
            if (payloadResult is BackupResult.Failure) {
                _statusMessage.value = "备份失败：${payloadResult.message ?: payloadResult.error.name}"
                onComplete(false)
                return@launch
            }
            val payload = (payloadResult as BackupResult.Success).value
            val result = withContext(Dispatchers.IO) { exporter.export(payload, outputStream) }
            when (result) {
                is BackupResult.Success -> {
                    repository.setLastBackupAt(System.currentTimeMillis())
                    _statusMessage.value = "备份成功（${payload.totalRecordCount} 条记录）"
                    onComplete(true)
                }
                is BackupResult.Failure -> {
                    _statusMessage.value = "导出失败：${result.message ?: result.error.name}"
                    onComplete(false)
                }
            }
        }
    }

    fun importNow(inputStream: java.io.InputStream, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { importer.import(inputStream) }
            when (result) {
                is BackupResult.Success -> {
                    _statusMessage.value = "恢复成功（${result.value.totalRecordCount} 条记录）"
                    onComplete(true)
                }
                is BackupResult.Failure -> {
                    _statusMessage.value = "恢复失败：${result.message ?: result.error.name}"
                    onComplete(false)
                }
            }
        }
    }

    /**
     * 合并 autoBackup + folderUri + lastBackupAt 三个 Flow 成一个 UI 状态
     */
    private fun combineBackupFlows(): kotlinx.coroutines.flow.Flow<BackupPreferencesUi> =
        kotlinx.coroutines.flow.combine(
            repository.autoBackupEnabled,
            repository.backupFolderUri,
            repository.lastBackupAt,
        ) { enabled, uri, lastAt ->
            BackupPreferencesUi(
                autoBackupEnabled = enabled,
                backupFolderUri = uri,
                lastBackupAt = lastAt,
            )
        }

    /**
     * Phase 8 Sub-step 8：合并 4 个 holiday Flow + 一次性的 maxYear 查询。
     *
     * - 4 个 Flow 任意变化都触发重算
     * - maxYear 通过 HolidayRepository.getMaxYear() 一次取（不参与 reactive flow,
     *   因为表内容主要在数据库 seed / 恢复备份时变化,日常使用固定）
     */
    private fun combineHolidayFlows(): kotlinx.coroutines.flow.Flow<HolidayPreferencesUi> =
        kotlinx.coroutines.flow.combine(
            repository.holidayAvoidEnabled,
            repository.holidayEveningReminderEnabled,
            repository.holidayEveningHour,
            repository.holidayEveningMinute,
        ) { avoidEnabled, eveningEnabled, hour, minute ->
            val maxYear = holidayRepository.getMaxYear()
            HolidayPreferencesUi(
                avoidEnabled = avoidEnabled,
                eveningEnabled = eveningEnabled,
                eveningHour = hour,
                eveningMinute = minute,
                maxYear = maxYear,
            )
        }
}

/** 备份偏好 UI 状态 */
data class BackupPreferencesUi(
    val autoBackupEnabled: Boolean = false,
    val backupFolderUri: String = "",
    val lastBackupAt: Long = 0L,
) {
    val hasFolderSelected: Boolean get() = backupFolderUri.isNotBlank()
    val neverBackedUp: Boolean get() = lastBackupAt == 0L
}

/**
 * Phase 8 Sub-step 8：节假日 / 智能提醒 偏好 UI 状态
 */
data class HolidayPreferencesUi(
    val avoidEnabled: Boolean = true,
    val eveningEnabled: Boolean = true,
    val eveningHour: Int = 21,
    val eveningMinute: Int = 0,
    val maxYear: Int? = null,
) {
    /** 把 hour/minute 格式化为 "HH:mm" */
    val eveningTimeLabel: String
        get() = "%02d:%02d".format(eveningHour, eveningMinute)

    /** 当前年份（设备本地时钟） */
    val currentYear: Int
        get() = LocalDate.now().year

    /** 是否需要提示用户联网同步（当前年 > 本地数据最大年） */
    val needsSync: Boolean
        get() = maxYear != null && currentYear > maxYear
}
