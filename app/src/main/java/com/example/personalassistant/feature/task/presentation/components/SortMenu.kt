package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.personalassistant.feature.task.domain.model.TaskSort

/**
 * 任务排序下拉菜单
 *
 * - 通过 [expanded] 控制显隐
 * - [onDismiss] 关闭菜单
 * - [onSortSelected] 选中某项时回调（回调里调用方需负责关闭菜单）
 *
 * 选项与 [TaskSort] 一一对应，label 在此组件里写死（避免污染 domain model）。
 */
@Composable
fun SortMenu(
    expanded: Boolean,
    currentSort: TaskSort,
    onSortSelected: (TaskSort) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        SortOption(TaskSort.PRIORITY_DUE_ASC, "优先级 + 截止日期", currentSort, onSortSelected, onDismiss)
        SortOption(TaskSort.DUE_DATE_ASC, "截止日期（早 → 晚）", currentSort, onSortSelected, onDismiss)
        SortOption(TaskSort.DUE_DATE_DESC, "截止日期（晚 → 早）", currentSort, onSortSelected, onDismiss)
        SortOption(TaskSort.CREATED_DESC, "创建时间（新 → 旧）", currentSort, onSortSelected, onDismiss)
        SortOption(TaskSort.TITLE_ASC, "标题（A → Z）", currentSort, onSortSelected, onDismiss)
        SortOption(TaskSort.MANUAL_ORDER, "自定义顺序（拖拽）", currentSort, onSortSelected, onDismiss)
    }
}

@Composable
private fun SortOption(
    sort: TaskSort,
    label: String,
    current: TaskSort,
    onSelect: (TaskSort) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            onSelect(sort)
            onDismiss()
        },
        trailingIcon = {
            if (sort == current) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                )
            }
        },
    )
}
