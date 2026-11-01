package com.example.personalassistant.feature.task.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.TaskCategory
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.presentation.components.SheetSection
import com.example.personalassistant.feature.task.presentation.components.TagChip
import com.example.personalassistant.feature.task.presentation.components.dueDateLabel
import com.example.personalassistant.feature.task.presentation.components.formatAmountNumber

/**
 * 清单项编辑弹窗（Phase 15）—— 标题 / 分类 / 截止时间 / 优先级 / 金额 / 标签。
 *
 * ## 两种模式
 *
 * - [item] 非 null = 编辑既有清单项
 * - [item] 为 null = 新建清单项（此时标签区不显示 —— 新项还没有 id，
 *   挂不上标签；数据层的 `addItemWithDetails` 也不支持带标签新建）
 *
 * ## 清空语义
 *
 * 数据层的 `updateItem` 里 null 表示「不改」，所以**清空**一个可空字段
 * 必须走 `clearItemFields`。本弹窗只负责产出**期望的最终值**，
 * 由调用方（`ChecklistTab`）比对原始值后决定是「改」还是「清」。
 *
 * @param item 被编辑的清单项（null = 新建）
 * @param availableTags 可选的标签（来自 UiState）
 * @param onDismiss 关闭
 * @param onSave 保存（[ChecklistItemEdit.id] 为 null 表示新建）
 * @param onDelete 删除（null = 不显示删除按钮）
 * @param onAttachTag 挂标签
 * @param onDetachTag 摘标签
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChecklistItemEditSheet(
    item: ChecklistItemUi?,
    availableTags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (ChecklistItemEdit) -> Unit,
    onDelete: (() -> Unit)? = null,
    onAttachTag: (Long) -> Unit = {},
    onDetachTag: (Long) -> Unit = {},
) {
    val isNew = item == null

    var title by remember { mutableStateOf(item?.title.orEmpty()) }
    var category by remember { mutableStateOf(item?.category) }
    var dueDate by remember { mutableStateOf(item?.dueDate) }
    var priority by remember { mutableStateOf(item?.priority ?: TaskPriority.MEDIUM) }
    var amountText by remember {
        mutableStateOf(item?.amount?.let { formatAmountNumber(it) }.orEmpty())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    // 标签的选中态本地维护 —— 挂标签是异步写库，等列表回流再刷新会「慢半拍」，
    // 这里先本地翻转，点完立刻有反馈。
    var attachedTagIds by remember {
        mutableStateOf(item?.tags?.map { it.id }?.toSet() ?: emptySet())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            // ===== 标题栏 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (isNew) R.string.checklist_create_title else R.string.checklist_edit_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                    )
                }
            }

            // ===== 标题 =====
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.checklist_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ===== 分类 =====
            SheetSection(title = stringResource(R.string.checklist_field_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text(stringResource(R.string.checklist_no_category)) },
                )
                TaskCategory.PRESETS.forEach { preset ->
                    FilterChip(
                        selected = category == preset.name,
                        onClick = { category = preset.name },
                        label = { Text(preset.name) },
                    )
                }
            }

            // ===== 截止时间 =====
            SheetSection(title = stringResource(R.string.checklist_field_due_date))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dueDate?.let { dueDateLabel(it) }
                        ?: stringResource(R.string.checklist_no_due_date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (dueDate != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                if (dueDate != null) {
                    IconButton(onClick = { dueDate = null }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.checklist_clear_due_date),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        stringResource(
                            if (dueDate == null) {
                                R.string.checklist_set_due_date
                            } else {
                                R.string.checklist_change_due_date
                            }
                        )
                    )
                }
            }

            // ===== 优先级 =====
            SheetSection(title = stringResource(R.string.checklist_field_priority))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                TaskPriority.entries.forEach { option ->
                    FilterChip(
                        selected = priority == option,
                        onClick = { priority = option },
                        label = { Text(option.label) },
                    )
                }
            }

            // ===== 金额 =====
            SheetSection(title = stringResource(R.string.checklist_field_amount))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                placeholder = { Text(stringResource(R.string.checklist_no_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // ===== 标签（仅编辑模式）=====
            if (!isNew) {
                SheetSection(title = stringResource(R.string.checklist_field_tags))
                if (availableTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.checklist_no_tags),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        availableTags.forEach { tag ->
                            val selected = attachedTagIds.contains(tag.id)
                            TagChip(
                                tag = tag,
                                selected = selected,
                                onClick = {
                                    attachedTagIds = if (selected) {
                                        attachedTagIds - tag.id
                                    } else {
                                        attachedTagIds + tag.id
                                    }
                                    if (selected) onDetachTag(tag.id) else onAttachTag(tag.id)
                                },
                            )
                        }
                    }
                }
            }

            // ===== 底部按钮 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.width(AppSpacing.xs))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        onSave(
                            ChecklistItemEdit(
                                id = item?.id,
                                title = title.trim(),
                                category = category,
                                dueDate = dueDate,
                                priority = priority,
                                amount = amountText.trim().toDoubleOrNull(),
                            )
                        )
                    },
                    enabled = title.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }

    // 日期选择器与底部弹窗**平级**渲染（不塞进弹窗内容里）——
    // Material3 的 DatePickerDialog 自己开一个 Dialog 窗口，
    // 嵌在 ModalBottomSheet 里会出现两层遮罩互相打架。
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dueDate = datePickerState.selectedDateMillis
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 清单项编辑结果 —— 弹窗产出、列表页消费的中转对象。
 *
 * @param id 清单项 id（null = 新建）
 * @param title 标题（已 trim）
 * @param category 分类（null = 无分类 → 需要走 clearItemFields 清）
 * @param dueDate 截止时间（null = 无截止 → 需要走 clearItemFields 清）
 * @param priority 优先级
 * @param amount 金额（null = 无金额 → 需要走 clearItemFields 清）
 */
data class ChecklistItemEdit(
    val id: Long? = null,
    val title: String,
    val category: String? = null,
    val dueDate: Long? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val amount: Double? = null,
)
