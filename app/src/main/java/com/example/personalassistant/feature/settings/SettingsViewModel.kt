package com.example.personalassistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.backup.di.BackupWorkerScheduler
import com.example.personalassistant.core.backup.exporter.BackupExporter
import com.example.personalassistant.core.backup.importer.BackupImporter
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.backup.serializer.BackupSerializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phase 8 Sub-step 4：备份设置 ViewModel
 *
 * 暴露：
 * - [themeMode]：主题模式（来自 SettingsRepository.themeMode）
 * - [backupState]：备份偏好（autoBackup / folderUri / lastBackupAt）
 * - [statusMessage]：操作反馈（成功/失败消息）
 * - [exportNow]：立即备份（用户给 OutputStream，由 ViewModel 写入 payload）
 * - [importNow]：立即导入（用户给 InputStream）
 * - [setAutoBackup]：切换自动备份（同时启用/禁用 Worker）
 * - [setBackupFolderUri]：更新目录 URI
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val serializer: BackupSerializer,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val backupWorkerScheduler: BackupWorkerScheduler,
) : ViewModel() {

    /** 主题模式：light / dark / system */
    val themeMode: StateFlow<String> = repository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "system"
        )

    /** 备份偏好快照 */
    val backupState: StateFlow<BackupPreferencesUi> = combineBackupFlows()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BackupPreferencesUi(),
        )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setThemeMode(mode: String) {
        viewModelScope.launch { repository.setThemeMode(mode) }
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
