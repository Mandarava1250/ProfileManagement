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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskViewMode
import com.example.personalassistant.feature.task.presentation.components.PriorityTabRow
import com.example.personalassistant.feature.task.presentation.components.QuadrantSection
import com.example.personalassistant.feature.task.presentation.components.RenameQuadrantAliasDialog
import com.example.personalassistant.feature.task.presentation.components.SortMenu
import com.example.personalassistant.feature.task.presentation.components.TagFilterBar
import com.example.personalassistant.feature.task.presentation.components.TaskFilterBar
import com.example.personalassistant.feature.task.presentation.components.TaskItem

/**
 * 任务 Tab 内容：顶部 FilterBar + TagFilterBar + **PriorityTabRow（Phase 8b'）** + 列表 + 折叠已完成
 *
 * Phase 4b：顶部 FilterBar（filter chips + sort 入口）+ 排序下拉菜单
 * Phase 6b：onEditTask 重命名为 onClickTask（编辑走详情页内编辑按钮）
 * Phase 6c：在 TaskFilterBar 下新增 [TagFilterBar]；onFilterChange 类型改 [FilterExpression]；
 *         新增 onTagToggle + onClearTag 用于标签筛选
 *
 * Phase 8b'：在 TagFilterBar 下新增 [PriorityTabRow] 6-Tab 切换 + 长按重命名 dialog；
 *           列表渲染根据 [TaskViewMode] 切换 ALL / BY_QUADRANT 两种模式；
 *           通过 5 个新回调（onShowAll / onSelectQuadrant / onLongPressQuadrant / onAliasChange / onDismissRename）
 *           驱动 [TaskViewModel]
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
    // ===== Phase 8b' =====
    onShowAll: () -> Unit,
    onSelectQuadrant: (TaskQuadrant) -> Unit,
    onAliasChange: (TaskQuadrant, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 长按哪个象限就弹哪个的 dialog；null = 不显示
    var renamingQuadrant by remember { mutableStateOf<TaskQuadrant?>(null) }

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

        // Phase 8b'：5 Tab 切换 + 任务计数
        PriorityTabRow(
            displayPreferences = state.displayPreferences,
            activeQuadrantCount = state.groups.byQuadrant.mapValues { it.value.size },
            onSelectAll = onShowAll,
            onSelectQuadrant = onSelectQuadrant,
            onLongPressQuadrant = { q -> renamingQuadrant = q },
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

        // Phase 8b'：重命名 dialog
        renamingQuadrant?.let { q ->
            val alias = TaskDisplayPreferences.aliasOf(state.displayPreferences, q)
            RenameQuadrantAliasDialog(
                quadrant = q,
                currentAlias = alias,
                onConfirm = { newAlias ->
                    onAliasChange(q, newAlias)
                    renamingQuadrant = null
                },
                onDismiss = { renamingQuadrant = null },
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
            // Phase 8b'：根据 viewMode 选择渲染策略
            when (state.displayPreferences.viewMode) {
                TaskViewMode.ALL -> {
                    // ALL：渲染所有象限（P1/P2/P3/P4/UNSCHEDULED）
                    TaskQuadrant.entries.forEach { quadrant ->
                        val tasks = state.groups.byQuadrant[quadrant].orEmpty()
                        if (tasks.isNotEmpty()) {
                            item(key = "header-${quadrant.name}") {
                                QuadrantSection(
                                    quadrant = quadrant,
                                    count = tasks.size,
                                    displayName = TaskDisplayPreferences.aliasOf(state.displayPreferences, quadrant),
                                )
                            }
                            items(tasks, key = { it.id }) { task ->
                                TaskRow(task, onToggleComplete, onClickTask, onDelete)
                            }
                        }
                    }
                }

                TaskViewMode.BY_QUADRANT -> {
                    // BY_QUADRANT：只渲染 selectedQuadrant（兜底 P1）
                    val target = state.displayPreferences.selectedQuadrant ?: TaskQuadrant.P1
                    val tasks = state.groups.byQuadrant[target].orEmpty()
                    if (tasks.isNotEmpty()) {
                        items(tasks, key = { it.id }) { task ->
                            TaskRow(task, onToggleComplete, onClickTask, onDelete)
                        }
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