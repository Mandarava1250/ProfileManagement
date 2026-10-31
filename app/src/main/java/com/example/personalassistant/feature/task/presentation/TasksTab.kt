package com.example.personalassistant.feature.task.presentation

import androidx.compose.foundation.layout.Arrangement
import com.example.personalassistant.core.ui.components.EmptyState
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import com.example.personalassistant.R
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskViewMode
import com.example.personalassistant.feature.task.presentation.components.MatrixViewToggle
import com.example.personalassistant.feature.task.presentation.components.PriorityTabRow
import com.example.personalassistant.feature.task.presentation.components.QuadrantSection
import com.example.personalassistant.feature.task.presentation.components.RenameQuadrantAliasDialog
import com.example.personalassistant.feature.task.presentation.components.SortMenu
import com.example.personalassistant.feature.task.presentation.components.TagFilterBar
import com.example.personalassistant.feature.task.presentation.components.TaskFilterBar
import com.example.personalassistant.feature.task.presentation.components.TaskItem
import com.example.personalassistant.feature.task.presentation.components.TaskMatrixView

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
 *
 * Phase 8final G2：新增 [MatrixViewToggle]（列表 / 矩阵）与 [onShowMatrix] 回调；
 *           [TaskViewMode.MATRIX] 下渲染 [TaskMatrixView]（2×2 分组 + 未排期独立区），
 *           数据与 ALL 模式完全一致，仅空间排布不同。矩阵模式下已完成区降级为计数条。
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
    // ===== Phase 8final G2 =====
    onShowMatrix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 长按哪个象限就弹哪个的 dialog；null = 不显示
    var renamingQuadrant by remember { mutableStateOf<TaskQuadrant?>(null) }

    // Phase 14：删除是破坏性操作 —— 点删除先进确认弹窗，确认后才真删
    var pendingDeleteTask by remember { mutableStateOf<Task?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部 FilterBar（分类）
        TaskFilterBar(
            currentFilter = state.filter,
            currentSort = state.sort,
            onFilterChange = onFilterChange,
            onSortClick = onSortClick,
            // Phase 14：排序菜单已经长在排序按钮上了，这里把显隐与选择接回去
            sortMenuExpanded = state.sortMenuExpanded,
            onSortSelected = onSortSelected,
            onSortDismiss = onSortDismiss,
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

        // Phase 14：长按象限重命名是个隐藏手势 —— 给一行常驻提示，
        // 不然没人知道还能这么用
        Text(
            text = stringResource(R.string.task_quadrant_rename_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        // Phase 8final G2：列表 / 矩阵 布局切换
        MatrixViewToggle(
            isMatrixMode = state.displayPreferences.viewMode == TaskViewMode.MATRIX,
            onSelectList = onShowAll,
            onSelectMatrix = onShowMatrix,
        )

        // Phase 14：SortMenu 已经挪进 TaskFilterBar（长在排序按钮上），这里不再单独挂一个空 Box

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

        // Phase 14：破坏性操作确认 —— 删除任务
        pendingDeleteTask?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDeleteTask = null },
                title = { Text(stringResource(R.string.task_delete_confirm_title)) },
                text = { Text(stringResource(R.string.task_delete_confirm_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDelete(target)
                            pendingDeleteTask = null
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteTask = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (state.isLoading) {
            // Phase 14：加载中给骨架屏，别让用户对着白屏发呆
            TaskListSkeleton()
            return@Column
        }

        if (state.groups.isEmpty) {
            EmptyTasksHint()
            return@Column
        }

        // Phase 8final G2：矩阵视图走独立布局（外层不可滚动，不能与下面的 LazyColumn 共用）
        if (state.displayPreferences.viewMode == TaskViewMode.MATRIX) {
            TaskMatrixView(
                matrix = state.matrix,
                displayPreferences = state.displayPreferences,
                onToggleComplete = onToggleComplete,
                onClickTask = onClickTask,
                onDelete = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            // 已完成区在矩阵模式下只给计数：2×2 外层不滚动，塞不下可展开列表，
            // 想看已完成请切回列表视图（取舍写进 TaskMatrixView 的 KDoc）
            CompletedCountHint(count = state.groups.completed.size)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Phase 14：底部留出 FAB 的位置，末条任务不会被悬浮按钮压住
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        ) {
            // Phase 8b'：根据 viewMode 选择渲染策略
            when (state.displayPreferences.viewMode) {
                TaskViewMode.ALL, TaskViewMode.MATRIX -> {
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
                                TaskRow(task, onToggleComplete, onClickTask, { pendingDeleteTask = it })
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
                            TaskRow(task, onToggleComplete, onClickTask, { pendingDeleteTask = it })
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
                        TaskRow(task, onToggleComplete, onClickTask, { pendingDeleteTask = it })
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
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
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
        EmptyState(
            illustration = R.drawable.ic_empty_tasks,
            title = stringResource(R.string.empty_tasks_title),
            description = stringResource(R.string.empty_tasks_desc),
        )
    }
}

/**
 * 矩阵视图下的已完成计数条（Phase 8final G2）。
 *
 * 只报数不展开：2×2 布局外层不可滚动，再塞一个可展开列表会把格子挤没。
 * 想看已完成的具体内容，切回列表视图（已完成折叠区在列表视图底部）。
 * count = 0 时整条隐藏，不占空间。
 */
@Composable
private fun CompletedCountHint(count: Int, modifier: Modifier = Modifier) {
    if (count == 0) return
    HorizontalDivider(modifier = modifier)
    Text(
        text = "已完成 $count 条（切回列表视图可展开）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Phase 14：任务列表骨架屏。
 *
 * 以前加载中直接什么都不渲染，看起来像页面坏了 —— 现在铺几张占位卡片。
 */
@Composable
private fun TaskListSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}