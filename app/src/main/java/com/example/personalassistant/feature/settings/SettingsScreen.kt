package com.example.personalassistant.feature.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.feature.profile.presentation.AccountSettingsCard
import com.example.personalassistant.feature.settings.theme.ThemeMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页（Phase 8 Sub-step 4+6+8 完整版）
 *
 * 内容：
 * - 记账账户（新增 Phase 3）：多账户列表 / 切换 / 新建 / 重命名 / 删除
 * - 账户头像（新增 Phase 4）：每行右侧相机按钮走 PhotoPicker，行内圆形缩略图可移除
 * - 主题切换（浅色/深色/系统）
 * - 智能提醒（新增 Sub-step 8）：节假日避让 / 提前通知 / 时间 / 同步
 * - 数据备份（Sub-step 4）：自动备份开关 + 备份目录 + 立即备份 / 立即恢复
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val holidayState by viewModel.holidayState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val currentAccountId by viewModel.currentAccountId.collectAsStateWithLifecycle()
    val pendingDeletion by viewModel.pendingDeletion.collectAsStateWithLifecycle()
    val syncPromptYear by viewModel.syncPromptYear.collectAsStateWithLifecycle()
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

    // ===== Phase 8 Sub-step 8：提前通知 TimePicker 状态 =====
    var showEveningTimePicker by remember { mutableStateOf(false) }

    // ===== Phase 4：账户头像 Picker =====
    //
    // Picker 回调里只能拿到选中的 Uri，拿不到「刚才点的是哪个账户」，
    // 所以用本地 state 把 accountId 暂存下来（不进 ViewModel：它是纯 UI 会话内状态，
    // 也不进备份，与 Phase 3 「当前账户走 DataStore 不走 Room」同一类判断）。
    var avatarTargetAccountId by remember { mutableStateOf<Long?>(null) }

    // PhotoPicker 无需运行时权限；它给的 content:// Uri 只有本次会话可读，
    // 而本功能会立即把它复制成自己的 file://（见 SetAccountAvatarUseCase），
    // 所以不需要 takePersistableUriPermission —— 这也是头像必须「拷一份」而不能只存引用的原因。
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        val target = avatarTargetAccountId
        avatarTargetAccountId = null
        if (picked != null && target != null) {
            viewModel.onAvatarPicked(target, picked)
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
            // ===== 记账账户（Phase 3：多账户 + 数据隔离）=====
            AccountSettingsCard(
                accounts = accounts,
                currentAccountId = currentAccountId,
                pendingDelete = pendingDeletion,
                onSwitch = viewModel::switchAccount,
                onCreate = viewModel::createAccount,
                onRename = viewModel::renameAccount,
                onDeleteRequest = viewModel::requestDeleteAccount,
                onConfirmDelete = viewModel::confirmDeleteAccount,
                onDismissDelete = viewModel::dismissDeleteAccount,
                onAvatarClick = { account ->
                    avatarTargetAccountId = account.id
                    avatarPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onAvatarRemove = viewModel::onAvatarRemoved,
            )

            // ===== 主题（Phase 8 Sub-step 6：ThemeMode + dynamicColor 持久化）=====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("主题", style = MaterialTheme.typography.titleMedium)
                    Spacer8()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemeMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier.padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = themePreferences.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                )
                                Text(themeModeLabel(mode))
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 动态色开关（Android 12+ Material You）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("动态配色", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Android 12+ 使用系统强调色，关闭后回到 App 自带色板",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = themePreferences.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    }
                }
            }

            // ===== 智能提醒（Phase 8 Sub-step 8）=====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        Spacer8()
                        Text("智能提醒", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer8()
                    Text(
                        text = "节假日避让 + 每日 21:00 提前通知明天/后天的节假日与调休。",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 节假日避让总开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("节假日避让", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "重复任务遇到法定节假日按各自策略避让",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = holidayState.avoidEnabled,
                            onCheckedChange = viewModel::setHolidayAvoidEnabled,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 提前通知开关 + 时间选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("每日提前通知", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (holidayState.eveningEnabled) {
                                    "每天 ${holidayState.eveningTimeLabel} 提醒明日节假日"
                                } else {
                                    "已关闭,不发送提前通知"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = holidayState.eveningEnabled,
                            onCheckedChange = viewModel::setHolidayEveningEnabled,
                        )
                    }

                    if (holidayState.eveningEnabled) {
                        Spacer8()
                        OutlinedButton(
                            onClick = { showEveningTimePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("调整时间（当前 ${holidayState.eveningTimeLabel}）")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 数据范围 + 同步入口
                    Text(
                        text = if (holidayState.maxYear != null) {
                            "节假日数据覆盖：${holidayState.maxYear} 年及之前"
                        } else {
                            "节假日数据为空,请联系开发者"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer8()
                    OutlinedButton(
                        onClick = viewModel::triggerHolidaySync,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                        Spacer8()
                        Text("立即同步节假日")
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

    // ===== Phase 8 Sub-step 8：弹窗 =====
    if (showEveningTimePicker) {
        EveningTimePickerDialog(
            initialHour = holidayState.eveningHour,
            initialMinute = holidayState.eveningMinute,
            onConfirm = { h, m ->
                viewModel.setHolidayEveningTime(h, m)
                showEveningTimePicker = false
            },
            onDismiss = { showEveningTimePicker = false },
        )
    }

    val syncYear = syncPromptYear
    if (syncYear != null) {
        HolidaySyncPromptDialog(
            year = syncYear,
            maxYear = holidayState.maxYear,
            onSyncNow = { viewModel.triggerHolidaySync() },
            onDismiss = { viewModel.dismissHolidaySyncPrompt() },
        )
    }
}

// ===== Phase 8 Sub-step 8：弹窗组件 =====

/**
 * 提前通知时间选择对话框（material3 TimePicker）。
 *
 * 确认时调 viewModel.setHolidayEveningTime(hour, minute) 持久化并重新调度 Worker。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EveningTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整提前通知时间") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/**
 * 主题模式 → 显示文案（Phase 8 Sub-step 6）
 *
 * 之所以放在 Composable 文件而不是 ThemeMode 枚举本身，
 * 是为了遵守"枚举本身与 Android string resource 解耦"的实践：
 * 主题 enum 是 domain 模型，文案归 UI 层。
 */
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    ThemeMode.SYSTEM -> "跟随系统"
}

/**
 * 节假日数据过期 → 联网同步授权弹窗（Phase 8 Sub-step 8）。
 *
 * 每年触发一次：当前年份 > 本地数据最大年份时在 init {} 期间被 ViewModel 设置 [syncPromptYear]。
 * 当前 Stub Gateway 不会真去联网，所以「立即同步」会回到 Toast「已同步」;后续 sub-step 替换为真实 API。
 */
@Composable
private fun HolidaySyncPromptDialog(
    year: Int,
    maxYear: Int?,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节假日数据即将过期") },
        text = {
            Text(
                buildString {
                    append("当前年份（$year）已超过本地预置数据范围（")
                    if (maxYear != null) append(maxYear) else append("无数据")
                    append("）。是否联网同步 $year 年的节假日？")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onSyncNow) { Text("立即同步") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        },
    )
}
