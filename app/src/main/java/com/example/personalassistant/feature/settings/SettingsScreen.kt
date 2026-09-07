package com.example.personalassistant.feature.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页（Phase 8 Sub-step 4 完整版）
 *
 * 内容：
 * - 主题切换（浅色/深色/系统）
 * - 数据备份（新增）：自动备份开关 + 备份目录 + 立即备份 / 立即恢复
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.dismissStatusMessage()
            }
        }
    }

    // ===== 选择备份目录（持久化 SAF 权限） =====
    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // 持久化 SAF 授权
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setBackupFolderUri(uri.toString())
        }
    }

    // ===== 立即备份 launcher（保存到 SAF） =====
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            val outputStream = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                viewModel.exportNow(outputStream) { /* success handled by snackbar */ }
            }
        }
    }

    // ===== 立即恢复 launcher（打开 .zip） =====
    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                viewModel.importNow(inputStream) { /* success handled by snackbar */ }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== 主题 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("主题", style = MaterialTheme.typography.titleMedium)
                    Spacer8()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统")
                            .forEach { (key, label) ->
                                Row(
                                    modifier = Modifier.padding(end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = themeMode == key,
                                        onClick = { viewModel.setThemeMode(key) },
                                    )
                                    Text(label)
                                }
                            }
                    }
                }
            }

            // ===== 数据备份（Phase 8 Sub-step 4） =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Backup, contentDescription = null)
                        Spacer8()
                        Text(
                            "数据备份",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer8()
                    Text(
                        "将课程/任务/事件/记账等数据导出为 .zip 文件，或从备份恢复。" +
                            "支持手动立即备份、自动每日备份到指定目录。",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 自动备份开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动备份", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "每 24 小时自动备份到所选目录",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = backupState.autoBackupEnabled,
                            onCheckedChange = viewModel::setAutoBackup,
                        )
                    }

                    Spacer8()

                    // 备份目录选择
                    OutlinedButton(
                        onClick = { openTreeLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (backupState.hasFolderSelected) "更改备份目录" else "选择备份目录")
                    }

                    backupState.backupFolderUri.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "当前目录：$it",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Spacer8()

                    // 上次备份时间
                    Text(
                        text = if (backupState.neverBackedUp) "尚未自动备份"
                        else "上次备份：${formatTimestamp(backupState.lastBackupAt)}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 立即备份 / 立即恢复
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                createBackupLauncher.launch("cyrene-backup-${System.currentTimeMillis()}.zip")
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null)
                            Spacer8()
                            Text("立即备份")
                        }
                        OutlinedButton(
                            onClick = {
                                openBackupLauncher.launch(arrayOf("application/zip"))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null)
                            Spacer8()
                            Text("从备份恢复")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
