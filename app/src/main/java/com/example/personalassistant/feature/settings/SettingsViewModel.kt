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
import com.example.personalassistant.feature.profile.data.AccountRepository
import com.example.personalassistant.feature.profile.data.CurrentAccountRepository
import com.example.personalassistant.feature.profile.data.SetAccountAvatarUseCase
import com.example.personalassistant.feature.profile.data.local.AccountIds
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import com.example.personalassistant.feature.profile.presentation.PendingAccountDeletion
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
 * - [accounts] / [currentAccountId]：Phase 3 记账账户列表与当前账户
 * - [pendingDeletion]：待二次确认的删除请求（null = 不弹框）
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
 * - [switchAccount] / [createAccount] / [renameAccount] /
 *   [requestDeleteAccount] / [confirmDeleteAccount] / [dismissDeleteAccount]：账户管理
 * - [onAvatarPicked] / [onAvatarRemoved]：Phase 4 账户头像
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
    private val accountRepository: AccountRepository,
    private val currentAccountRepository: CurrentAccountRepository,
    private val setAccountAvatar: SetAccountAvatarUseCase,
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

    // ===== Phase 3：记账账户 =====

    /** 全部账户（Room Flow，新建/删除后自动重发） */
    val accounts: StateFlow<List<UserProfileEntity>> = accountRepository.observeAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    /** 当前账户 id（DataStore Flow） */
    val currentAccountId: StateFlow<Long> = currentAccountRepository.currentAccountIdFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AccountIds.DEFAULT_ACCOUNT_ID,
        )

    /** 待二次确认的删除请求（null = 不弹删除确认框） */
    private val _pendingDeletion = MutableStateFlow<PendingAccountDeletion?>(null)
    val pendingDeletion: StateFlow<PendingAccountDeletion?> = _pendingDeletion.asStateFlow()

    fun switchAccount(accountId: Long) {
        viewModelScope.launch {
            currentAccountRepository.setCurrentAccountId(accountId)
            // 不刷记账页：FinanceRepositoryImpl 的 flatMapLatest 会自动重发读流
        }
    }

    fun createAccount(name: String) {
        viewModelScope.launch {
            runCatching { accountRepository.createAccount(name) }
                .onSuccess {
                    _statusMessage.value = "已创建账户「${name.trim()}」"
                }
                .onFailure { e ->
                    _statusMessage.value = "创建失败：${e.message ?: "账户名不合法"}"
                }
        }
    }

    fun renameAccount(accountId: Long, name: String) {
        viewModelScope.launch {
            runCatching { accountRepository.renameAccount(accountId, name) }
                .onSuccess { _statusMessage.value = "已重命名" }
                .onFailure { e -> _statusMessage.value = "重命名失败：${e.message}" }
        }
    }

    /**
     * 用户点了删除按钮：先查该账户流水数，回填给二次确认文案。
     *
     * 不在此处直接删 —— 删账户不可恢复，必须让 UI 再确认一次。
     */
    fun requestDeleteAccount(account: UserProfileEntity) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                accountRepository.transactionCount(account.id)
            }
            _pendingDeletion.value = PendingAccountDeletion(
                accountId = account.id,
                name = account.name,
                transactionCount = count,
            )
        }
    }

    fun confirmDeleteAccount() {
        val pending = _pendingDeletion.value ?: return
        viewModelScope.launch {
            runCatching { accountRepository.deleteAccount(pending.accountId) }
                .onSuccess {
                    _pendingDeletion.value = null
                    _statusMessage.value = "已删除账户「${pending.name}」及其记账数据"
                }
                .onFailure { e ->
                    _pendingDeletion.value = null
                    _statusMessage.value = "删除失败：${e.message ?: "至少需要保留一个账户"}"
                }
        }
    }

    fun dismissDeleteAccount() {
        _pendingDeletion.value = null
    }

    // ===== Phase 4：账户头像 =====

    /**
     * 用户从 PhotoPicker 选完图。
     *
     * 真正的「先存新 → 再写库 → 后删旧」顺序守卫在 [SetAccountAvatarUseCase] 里，
     * 本方只负责把失败翻译成可理解的提示（不删旧像是关键不变量，不能在这里自己拼）。
     */
    fun onAvatarPicked(accountId: Long, source: android.net.Uri) {
        viewModelScope.launch {
            val ok = runCatching { setAccountAvatar(accountId, source) }
                .getOrElse { e ->
                    _statusMessage.value = "头像设置失败：${e.message ?: "账户不存在"}"
                    return@launch
                }
            _statusMessage.value = if (ok) "头像已更新" else "无法读取所选图片，已保留原头像"
        }
    }

    /** 移除头像（连带删文件） */
    fun onAvatarRemoved(accountId: Long) {
        viewModelScope.launch {
            val ok = runCatching { setAccountAvatar.clear(accountId) }.getOrDefault(false)
            _statusMessage.value = if (ok) "已移除头像" else "移除失败：账户不存在"
        }
    }

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
