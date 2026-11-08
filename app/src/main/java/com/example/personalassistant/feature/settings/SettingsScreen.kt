package com.example.personalassistant.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import java.io.InputStream
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.BigTitleHeader
import com.example.personalassistant.core.ui.components.UnderlineTabs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.feature.settings.dialogs.DoneDialog
import com.example.personalassistant.feature.settings.dialogs.EveningTimePickerDialog
import com.example.personalassistant.feature.settings.dialogs.HolidaySyncPromptDialog
import com.example.personalassistant.feature.settings.dialogs.LongPressConfirmDialog
import com.example.personalassistant.feature.settings.dialogs.SuggestBackupBeforeResetDialog
import com.example.personalassistant.feature.settings.sections.CalendarSection
import com.example.personalassistant.feature.settings.sections.ChecklistSection
import com.example.personalassistant.feature.settings.sections.FinanceSection
import com.example.personalassistant.feature.settings.sections.ProfileSection
import com.example.personalassistant.feature.settings.sections.TaskSection
import com.example.personalassistant.feature.settings.sections.TimetableSection
import com.example.personalassistant.navigation.Screen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 设置中心（Phase 13 重构）
 *
 * ## 定位
 *
 * 底部导航的第 6 个 Tab —— 与其它 5 个 Tab 平级，**不是**「我」页的二级页了。
 *
 * ## 结构
 *
 * 一个长页面，按模块分成五块：**课表 / 任务 / 记账 / 日历 / 我的**。
 * 每块的长期设置都在自己那块里改，不再散落到各功能页。
 *
 * 分块实现在 `sections/` 下，本文件只做壳：
 * - 组装 5 个 section
 * - 持有必须留在 Composable 里的 ActivityResultLauncher（SAF / PhotoPicker）
 * - 承载跨分组的弹窗（清除数据三阶段、提前通知时间选择、节假日同步提示）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel(),
    cutoffViewModel: MonthCutoffViewModel = hiltViewModel(),
) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val holidayState by viewModel.holidayState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val currentAccountId by viewModel.currentAccountId.collectAsStateWithLifecycle()
    val pendingDeletion by viewModel.pendingDeletion.collectAsStateWithLifecycle()
    val syncPromptYear by viewModel.syncPromptYear.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val resetState by viewModel.resetState.collectAsStateWithLifecycle()

    val cutoffPolicy by cutoffViewModel.cutoffPolicy.collectAsStateWithLifecycle()
    val cutoffDayText by cutoffViewModel.cutoffDayText.collectAsStateWithLifecycle()
    val cutoffMessage by cutoffViewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ===== 分组导航栏的滚动状态 =====
    //
    // 内容用 LazyColumn（而不是 Column + verticalScroll）才能按分组索引跳转。
    val listState = rememberLazyListState()
    var selectedSectionIndex by remember { mutableIntStateOf(0) }
    // 选中态跟随滚动位置：手动滑过去时高亮也跟着走
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { selectedSectionIndex = it }
    }

    // 操作反馈（账户改名、备份结果、截止日保存…）统一走 snackbar
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.dismissStatusMessage()
            }
        }
    }
    LaunchedEffect(cutoffMessage) {
        cutoffMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                cutoffViewModel.dismissMessage()
            }
        }
    }

    // ===== 选择备份目录（持久化 SAF 权限）=====
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

    // ===== 立即备份 launcher（保存到 SAF）=====
    // Phase 14：快速替换是破坏性操作 —— 选好文件后先确认，确认前把流留着
    var pendingImportStream by remember { mutableStateOf<InputStream?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
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

    // ===== 立即恢复 launcher（打开 .zip，走「快速替换」路径：整表替换）=====
    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                // Phase 14：快速替换会清空所有表 —— 先确认，再真的导入
                pendingImportStream = inputStream
                showImportConfirm = true
            }
        }
    }

    // 「预览后导入」不需要额外 launcher —— 它直接 navigate 到 BackupPreview 页，
    // 该页自己拉 SAF（见 BackupPreviewScreen）。上面的 launcher 只为「快速替换」服务。

    // ===== 提前通知 TimePicker 状态 =====
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
            // Tab 页不放返回箭头 —— 与其它 5 个 Tab 保持一致。
            BigTitleHeader(title = stringResource(R.string.settings_title))
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ===== 分组导航栏（固定在顶部，点它跳到对应分组）=====
            SettingsNavBar(
                selectedIndex = selectedSectionIndex,
                onSelect = { index ->
                    scope.launch { listState.animateScrollToItem(index) }
                },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // ===== 课表 =====
                item(key = "section_timetable") {
                    TimetableSection(navController = navController)
                }

                // ===== 任务 =====
                item(key = "section_task") {
                    TaskSection()
                }

                // ===== Phase 22.5：清单 =====
                item(key = "section_checklist") {
                    ChecklistSection()
                }

                // ===== 记账 =====
                item(key = "section_finance") {
                    FinanceSection(
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
                        onSetInitialBalance = viewModel::setInitialBalance,
                        cutoffPolicy = cutoffPolicy,
                        cutoffDayText = cutoffDayText,
                        onCutoffDayTextChange = cutoffViewModel::setCutoffDayText,
                        currentCycleYm = cutoffViewModel.currentCycleYm,
                        draftCutoffDay = cutoffViewModel.draftCutoffDay(),
                        onSaveCutoff = cutoffViewModel::save,
                        onOpenCategory = { navController.navigate(Screen.FinanceCategory.route) },
                        onOpenBudget = { navController.navigate(Screen.FinanceBudget.route) },
                    )
                }

                // ===== 日历 =====
                item(key = "section_calendar") {
                    CalendarSection(
                        holidayState = holidayState,
                        onAvoidEnabledChange = viewModel::setHolidayAvoidEnabled,
                        onEveningEnabledChange = viewModel::setHolidayEveningEnabled,
                        onPickEveningTime = { showEveningTimePicker = true },
                        onSyncHolidays = viewModel::triggerHolidaySync,
                    )
                }

                // ===== 我的 =====
                item(key = "section_profile") {
                    ProfileSection(
                        themePreferences = themePreferences,
                        onThemeModeChange = viewModel::setThemeMode,
                        onDynamicColorChange = viewModel::setDynamicColor,
                        onOpenGpaRule = { navController.navigate(Screen.EditGpaRule.route) },
                        backupState = backupState,
                        onAutoBackupChange = viewModel::setAutoBackup,
                        onPickBackupFolder = { openTreeLauncher.launch(null) },
                        onBackupNow = {
                            createBackupLauncher.launch("cyrene-backup-${System.currentTimeMillis()}.zip")
                        },
                        onPreviewRestore = { navController.navigate(Screen.BackupPreview.route) },
                        onQuickRestore = { openBackupLauncher.launch(arrayOf("application/zip")) },
                        resetRunning = resetState is SettingsViewModel.ResetUiState.Running,
                        onRequestReset = viewModel::requestReset,
                    )
                }
            }
        }
    }

    // ===== 清除数据的三阶段弹窗 =====
    // Phase 14：快速替换前确认 —— 说明会覆盖当前全部数据
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                pendingImportStream?.close()
                pendingImportStream = null
                showImportConfirm = false
            },
            title = { Text(stringResource(R.string.import_replace_confirm_title)) },
            text = { Text(stringResource(R.string.import_replace_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val stream = pendingImportStream
                        pendingImportStream = null
                        showImportConfirm = false
                        if (stream != null) {
                            viewModel.importNow(stream) { /* success handled by snackbar */ }
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingImportStream?.close()
                        pendingImportStream = null
                        showImportConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    when (val state = resetState) {
        is SettingsViewModel.ResetUiState.Idle -> Unit
        is SettingsViewModel.ResetUiState.SuggestBackup -> {
            SuggestBackupBeforeResetDialog(
                onBackupThenReset = {
                    // 直接复用 createBackupLauncher，先备份再清
                    createBackupLauncher.launch(
                        "cyrene-backup-${System.currentTimeMillis()}.zip"
                    )
                    viewModel.cancelReset()
                },
                onSkipBackup = viewModel::skipBackupAndReset,
                onCancel = viewModel::cancelReset,
            )
        }
        is SettingsViewModel.ResetUiState.LongPress -> {
            LongPressConfirmDialog(
                onLongPressComplete = viewModel::confirmResetAfterLongPress,
                onCancel = viewModel::cancelReset,
            )
        }
        is SettingsViewModel.ResetUiState.Running -> {
            // Phase 14：加进度指示 —— 清除要跑一会儿，只有一句「正在清除…」用户不知道卡到哪了
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.reset_clearing_progress)) },
                text = {
                    Column {
                        Text("正在清空 14 张表与全部偏好，请稍候。")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {},
            )
        }
        is SettingsViewModel.ResetUiState.Done -> {
            DoneDialog(
                allSucceeded = state.report.allSucceeded,
                detail = "db=${state.report.databaseCleared}, " +
                    "ds=${state.report.datastoreCleared}, " +
                    "att=${state.report.attachmentsCleared}, " +
                    "avt=${state.report.avatarsCleared}",
                onAck = viewModel::acknowledgeResetDone,
            )
        }
    }

    // ===== 提前通知时间选择 =====
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

    // ===== 节假日数据过期 → 联网同步提示 =====
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

/**
 * 分组标题（顺序必须与 LazyColumn 里的 item 顺序一致）
 *
 * 索引 → 分组：0 课表 / 1 任务 / 2 记账 / 3 日历 / 4 我的
 */
private val SETTINGS_SECTION_TITLES = listOf("课表", "任务", "记账", "日历", "我的")

/**
 * 设置页顶部的分组导航栏
 *
 * 固定在顶部、不随内容滚动；点一下就滚到对应分组。
 * 选中态由外部传入 —— 它跟随内容滚动位置，所以手动滑过去时高亮也会跟着走。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsNavBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    UnderlineTabs(
        tabs = SETTINGS_SECTION_TITLES,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
    )
}