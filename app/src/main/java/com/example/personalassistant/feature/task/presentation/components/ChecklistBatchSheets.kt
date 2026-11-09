package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.TaskListWithCount
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 18.6：批量操作的选择面板。
 *
 * 四个 BottomSheet（改日期 / 改优先级 / 移入清单 / 加标签）+ 一个删除确认框，
 * 视觉语言跟 [com.example.personalassistant.feature.task.presentation.ChecklistFilterSheet]
 * 一致：ModalBottomSheet + 小节标题 + FlowRow 里的 FilterChip。
 *
 * 日期只给**快捷选项**（今天 / 明天 / 本周末 / 下周一 / 无截止）——
 * 批量场景下要的是「把这几项一起挪到同一天」，不是给每一项单独挑时间。
 */

/** 批量改截止时间。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChecklistBatchDateSheet(
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    // 本周末 = 还没过去的最近一个周日
    val sunday = today.plusDays((DayOfWeek.SUNDAY.value - today.dayOfWeek.value + 7) % 7L)
    val nextMonday = today.plusDays((DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7L)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            SheetSection(title = "改截止时间")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                BatchDateChip("今天") { onPick(endOfDay(today, zone)) }
                BatchDateChip("明天") { onPick(endOfDay(today.plusDays(1), zone)) }
                BatchDateChip("本周末") { onPick(endOfDay(sunday, zone)) }
                BatchDateChip("下周一") { onPick(endOfDay(nextMonday, zone)) }
                BatchDateChip("无截止时间") { onPick(null) }
            }
        }
    }
}

/** 批量改优先级。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChecklistBatchPrioritySheet(
    onPick: (TaskPriority) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            SheetSection(title = "改优先级")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                TaskPriority.entries.forEach { priority ->
                    FilterChip(
                        selected = false,
                        onClick = { onPick(priority) },
                        label = { Text(priority.label) },
                    )
                }
            }
        }
    }
}

/** 批量移入别的清单（归属地）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChecklistBatchListSheet(
    lists: List<TaskListWithCount>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            SheetSection(title = "移入清单")
            if (lists.isEmpty()) {
                Text(
                    text = "还没有其它清单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    lists.forEach { entry ->
                        FilterChip(
                            selected = false,
                            onClick = { onPick(entry.list.id) },
                            label = { Text(entry.list.displayName) },
                        )
                    }
                }
            }
        }
    }
}

/** 批量加标签。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChecklistBatchTagSheet(
    tags: List<Tag>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            SheetSection(title = "加标签")
            if (tags.isEmpty()) {
                Text(
                    text = "还没有标签",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = { onPick(tag.id) },
                            label = { Text(tag.displayName) },
                        )
                    }
                }
            }
        }
    }
}

/** 批量删除的确认框（删了不可逆 —— 至少问一句）。 */
@Composable
internal fun ChecklistBatchDeleteDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除 $count 项？") },
        text = { Text("删掉后不能恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 日期快捷项的 chip。 */
@Composable
private fun BatchDateChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
    )
}

/** 某一天 23:59:59.999 的毫秒时间戳（截止时间统一落在当天末尾）。 */
private fun endOfDay(date: LocalDate, zone: ZoneId): Long =
    date.atTime(23, 59, 59, 999).atZone(zone).toInstant().toEpochMilli()
