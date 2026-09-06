package com.example.personalassistant.feature.task.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.presentation.components.QuadrantSection
import com.example.personalassistant.feature.task.presentation.components.SortMenu
import com.example.personalassistant.feature.task.presentation.components.TaskFilterBar
import com.example.personalassistant.feature.task.presentation.components.TaskItem

/**
 * 任务 Tab 内容：顶部 FilterBar + P1-P4 + 未排期 分组 + 底部折叠已完成
 *
 * Phase 4b 新增：顶部 FilterBar（filter chips + sort 入口）+ 排序下拉菜单
 *
 * Phase 6b：onEditTask 重命名为 onClickTask（语义变为"查看任务详情"，编辑走详情页内编辑按钮）。
 */
@Composable
fun TasksTab(
    state: TaskUiState,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClickTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
    onToggleShowCompleted: () -> Unit,
    onFilterChange: (com.example.personalassistant.feature.task.domain.model.TaskFilter) -> Unit,
    onSortClick: () -> Unit,
    onSortSelected: (com.example.personalassistant.feature.task.domain.model.TaskSort) -> Unit,
    onSortDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部 FilterBar
        TaskFilterBar(
            currentFilter = state.filter,
            currentSort = state.sort,
            onFilterChange = onFilterChange,
            onSortClick = onSortClick,
        )

        // SortMenu（挂在 FilterBar 的位置之外，由 Box 锚定）
        // 这里采用 DropdownMenu + 父级 Box 锚点的方式
        // 为了简化，这里放在 Column 之后通过 BoxAnchor 实现
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
            EmptyTasksHint(modifier = Modifier.fillMaxSize())
            return@Column
        }

        val orderedQuadrants = listOf(
            TaskQuadrant.P1,
            TaskQuadrant.P2,
            TaskQuadrant.P3,
            TaskQuadrant.P4,
            TaskQuadrant.UNSCHEDULED,
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            orderedQuadrants.forEach { q ->
                val tasks = state.groups.byQuadrant[q].orEmpty()
                if (tasks.isNotEmpty()) {
                    item(key = "header_${q.name}") {
                        QuadrantSection(quadrant = q, count = tasks.size)
                    }
                    items(tasks, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            onToggleComplete = { onToggleComplete(task.id, it) },
                            onClick = { onClickTask(task.id) },
                            onDelete = { onDelete(task) },
                        )
                    }
                }
            }

            val completed = state.groups.completed
            if (completed.isNotEmpty()) {
                item(key = "completed_header") {
                    CompletedHeader(
                        count = completed.size,
                        expanded = state.showCompleted,
                        onToggle = onToggleShowCompleted,
                    )
                }
                if (state.showCompleted) {
                    items(completed, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            onToggleComplete = { onToggleComplete(task.id, it) },
                            onClick = { onClickTask(task.id) },
                            onDelete = { onDelete(task) },
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起已完成" else "展开已完成",
            )
        }
        Text(
            text = "已完成 $count",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
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
