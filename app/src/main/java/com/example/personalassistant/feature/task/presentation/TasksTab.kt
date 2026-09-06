package com.example.personalassistant.feature.task.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.presentation.components.QuadrantSection
import com.example.personalassistant.feature.task.presentation.components.SortMenu
import com.example.personalassistant.feature.task.presentation.components.TagFilterBar
import com.example.personalassistant.feature.task.presentation.components.TaskFilterBar
import com.example.personalassistant.feature.task.presentation.components.TaskItem

/**
 * 任务 Tab 内容：顶部 FilterBar + TagFilterBar + P1-P4 + 未排期 分组 + 底部折叠已完成
 *
 * Phase 4b：顶部 FilterBar（filter chips + sort 入口）+ 排序下拉菜单
 * Phase 6b：onEditTask 重命名为 onClickTask（编辑走详情页内编辑按钮）
 * Phase 6c：在 TaskFilterBar 下新增 [TagFilterBar]；onFilterChange 类型改 [FilterExpression]；
 *         新增 onTagToggle + onClearTag 用于标签筛选
 */
@Composable
fun TasksTab(
    state: TaskUiState,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClickTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
    onToggleShowCompleted: () -> Unit,
    onFilterChange: (FilterExpression) -> Unit,
    onTagToggle: (Long) -> Unit,
    onClearTag: () -> Unit,
    onSortClick: () -> Unit,
    onSortSelected: (TaskSort) -> Unit,
    onSortDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部 FilterBar（分类）
        TaskFilterBar(
            currentFilter = state.filter,
            currentSort = state.sort,
            onFilterChange = onFilterChange,
            onSortClick = onSortClick,
        )

        // 顶部 TagFilterBar（Phase 6c：标签云 chip 行；空时自动隐藏）
        TagFilterBar(
            tags = state.tagsWithCount,
            selectedTagId = state.selectedTagId,
            onTagToggle = onTagToggle,
            onClear = onClearTag,
        )

        // SortMenu（挂在 FilterBar 的位置之外，由 Box 锚定）
        Box(modifier = Modifier.fillMaxWidth()) {
            SortMenu(
                expanded = state.sortMenuExpanded,
                currentSort = state.sort,
                onSortSelected = onSortSelected,
                onDismiss = onSortDismiss,
            )
        }

        if (state.isLoading) {
            // 极短暂占位；Flow 第一次发射前不会出现
            return@Column
        }

        if (state.groups.isEmpty) {
            EmptyTasksHint()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // 按象限顺序渲染 P1, P2, P3, P4 + UNSCHEDULED
            TaskQuadrant.entries.forEach { quadrant ->
                val tasks = state.groups.byQuadrant[quadrant].orEmpty()
                if (tasks.isNotEmpty()) {
                    item(key = "header-${quadrant.name}") {
                        QuadrantSection(
                            quadrant = quadrant,
                            count = tasks.size,
                        )
                    }
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(task, onToggleComplete, onClickTask, onDelete)
                    }
                }
            }

            // 已完成区（底部折叠）
            if (state.groups.completed.isNotEmpty()) {
                item(key = "completed-header") {
                    CompletedHeader(
                        expanded = state.showCompleted,
                        count = state.groups.completed.size,
                        onToggle = onToggleShowCompleted,
                    )
                }
                if (state.showCompleted) {
                    items(state.groups.completed, key = { "completed-${it.id}" }) { task ->
                        TaskRow(task, onToggleComplete, onClickTask, onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClickTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
) {
    TaskItem(
        task = task,
        onToggleComplete = { onToggleComplete(task.id, it) },
        onClick = { onClickTask(task.id) },
        onDelete = { onDelete(task) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun CompletedHeader(
    expanded: Boolean,
    count: Int,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已完成",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun EmptyTasksHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还没有任务 ♪",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "点击右下角 + 添加你的第一个任务",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}