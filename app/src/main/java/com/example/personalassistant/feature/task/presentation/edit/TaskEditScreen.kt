package com.example.personalassistant.feature.task.presentation.edit

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.AppTopBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.core.holiday.model.HolidayStrategy
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.presentation.edit.components.CategoryPickerSheet
import com.example.personalassistant.feature.task.presentation.edit.components.ReminderTimePickerSheet
import com.example.personalassistant.feature.task.presentation.edit.components.RepeatRulePickerSheet

/**
 * 任务编辑页（新建 / 编辑复用）
 *
 * - 顶部 AppBar：返回 + 标题（"新建任务" / "编辑任务"）+ 保存按钮（编辑模式额外有删除）
 * - 主体：标题 / 描述 / 优先级 / 截止日期 / 子清单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    taskId: Long,
    onBack: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // ===== Phase 5b：通知权限申请 launcher =====
    // - rememberLauncherForActivityResult 把 Activity 的 onActivityResult 桥接到 Composable
    // - onResult 直接回调 VM；launcher 本身不持有 VM 强引用，VM 重建不影响 launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onPermissionResult,
    )
    // - LaunchedEffect 监听一次性信号 shouldRequestNotificationPermission
    // - true → launcher.launch() 触发系统弹框；launch 后立刻 consume() 置 false 防重组重放
    LaunchedEffect(state.shouldRequestNotificationPermission) {
        if (state.shouldRequestNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.consumeShouldRequestNotificationPermission()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(if (state.isNew) stringResource(R.string.task_new_title) else stringResource(R.string.task_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "删除",
                            )
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text("保存")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== 标题 =====
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("标题") },
                singleLine = true,
                isError = state.titleError != null,
                supportingText = state.titleError?.let { msg -> { Text(msg) } },
                modifier = Modifier.fillMaxWidth(),
            )

            // ===== 描述 =====
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("描述（可选）") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            // ===== 优先级 =====
            Text(
                text = "优先级",
                style = MaterialTheme.typography.titleSmall,
            )
            val priorities = TaskPriority.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                priorities.forEachIndexed { index, priority ->
                    SegmentedButton(
                        selected = state.priority == priority,
                        onClick = { viewModel.onPriorityChange(priority) },
                        shape = SegmentedButtonDefaults.itemShape(index, priorities.size),
                    ) {
                        Text(priority.label)
                    }
                }
            }

            // ===== 截止日期 =====
            Text(
                text = "截止日期",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.dueDateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (state.dueDate != null) {
                    IconButton(onClick = viewModel::clearDueDate) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "清除截止日期",
                        )
                    }
                }
                TextButton(onClick = viewModel::toggleDatePicker) {
                    Text(if (state.dueDate == null) "设置" else "更改")
                }
            }

            // ===== 分类（点击弹出 CategoryPickerSheet）=====
            FieldRow(
                label = "分类",
                value = state.category ?: "无分类",
                onClick = viewModel::toggleCategorySheet,
            )

            // ===== 重复规则（点击弹出 RepeatRulePickerSheet）=====
            FieldRow(
                label = "重复",
                value = state.repeatRuleLabel,
                onClick = viewModel::toggleRepeatRuleSheet,
            )

            // ===== 节假日策略（Phase 8 Sub-step 8，重复任务才生效）=====
            HolidayStrategyPicker(
                current = state.holidayStrategy,
                enabled = state.isHolidayStrategyEnabled,
                onChange = viewModel::onHolidayStrategyChange,
            )

            // ===== 提醒时间（点击弹出 ReminderTimePickerSheet）=====
            FieldRow(
                label = "提醒",
                value = state.reminderLabel,
                onClick = viewModel::toggleReminderPicker,
            )

            // ===== 子清单 =====
            Text(
                text = "子清单",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = state.newSubTaskInput,
                    onValueChange = viewModel::onNewSubTaskInputChange,
                    placeholder = { Text("添加清单项…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                IconButton(
                    onClick = viewModel::addSubTask,
                    enabled = state.newSubTaskInput.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "添加清单项",
                    )
                }
            }

            if (state.subTasks.isEmpty()) {
                Text(
                    text = "还没有清单项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.subTasks.forEach { item ->
                    SubTaskRow(
                        item = item,
                        onToggle = { viewModel.toggleSubTaskComplete(item.id) },
                        onDelete = { viewModel.deleteSubTask(item.id) },
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // ===== DatePicker 弹窗 =====
    if (state.showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.dueDate)
        DatePickerDialog(
            onDismissRequest = viewModel::toggleDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDateSelected(datePickerState.selectedDateMillis)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::toggleDatePicker) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ===== CategoryPickerSheet =====
    if (state.showCategorySheet) {
        CategoryPickerSheet(
            currentCategoryName = state.category,
            categories = state.categoryChips,
            onSelect = { viewModel.onCategoryChange(it) },
            onDismiss = viewModel::toggleCategorySheet,
        )
    }

    // ===== RepeatRulePickerSheet =====
    if (state.showRepeatRuleSheet) {
        RepeatRulePickerSheet(
            current = state.repeatRule,
            onConfirm = { viewModel.onRepeatRuleChange(it) },
            onDismiss = viewModel::toggleRepeatRuleSheet,
        )
    }

    // ===== ReminderTimePickerSheet =====
    if (state.showReminderPicker) {
        ReminderTimePickerSheet(
            initialMillis = state.reminderTime,
            onConfirm = { viewModel.onReminderChange(it) },
            onDismiss = viewModel::toggleReminderPicker,
        )
    }
}

/**
 * 编辑页字段行：左侧 label + 右侧 value + 点击触发回调。
 *
 * 用于分类 / 重复 / 提醒等"间接编辑"字段。
 */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubTaskRow(
    item: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.Checkbox(
            checked = item.isCompleted,
            onCheckedChange = { onToggle() },
        )
        Text(
            text = item.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            color = if (item.isCompleted)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

/**
 * Phase 8 Sub-step 8：节假日策略 RadioButton 组（4 选 1）。
 *
 * - 仅当 [enabled] == true 时（即任务是重复任务），控件可点击
 * - 整体非重复任务时仍渲染控件,但 alpha 偏低 + onChange 不响应,保证"已存在的策略字段"用户能看见
 */
@Composable
private fun HolidayStrategyPicker(
    current: HolidayStrategy,
    enabled: Boolean,
    onChange: (HolidayStrategy) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = "遇到节假日",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (enabled) {
                "重复任务在法定节假日当天的提醒方式"
            } else {
                "非重复任务不生效,设置仅作保留"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        HolidayStrategy.entries.forEach { strategy ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onChange(strategy) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(
                    selected = current == strategy,
                    onClick = if (enabled) ({ onChange(strategy) }) else null,
                    enabled = enabled,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = holidayStrategyLabel(strategy),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * HolidayStrategy 显示文案（与 [TaskEditUiState.holidayStrategyLabel] 等价，
 * 拆出独立函数便于复用与单测）。
 */
private fun holidayStrategyLabel(strategy: HolidayStrategy): String = when (strategy) {
    HolidayStrategy.SKIP -> "跳过本次提醒"
    HolidayStrategy.ADVANCE -> "提前到上一个工作日"
    HolidayStrategy.POSTPONE -> "推迟到下一个工作日"
    HolidayStrategy.KEEP_NORMAL -> "照常提醒(不休假)"
}