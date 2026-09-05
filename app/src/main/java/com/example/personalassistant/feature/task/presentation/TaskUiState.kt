package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskGroups
import com.example.personalassistant.feature.task.domain.model.TaskSort

/**
 * 任务主页 UI 状态
 *
 * - isLoading：初始状态（DB 还没回值）
 * - groups：P1-P4 + 未排期 + 已完成 分组结果
 * - showCompleted：是否展开底部的"已完成"折叠区
 *
 * Phase 4b 新增：
 * - filter：当前筛选条件（All / ByCategory / ...）
 * - sort：当前排序方式
 * - sortMenuExpanded：排序菜单是否展开（局部 UI 态）
 */
data class TaskUiState(
    val isLoading: Boolean = true,
    val groups: TaskGroups = TaskGroups(emptyMap(), emptyList()),
    val showCompleted: Boolean = false,
    val filter: TaskFilter = TaskFilter.ActiveOnly,
    val sort: TaskSort = TaskSort.DEFAULT,
    val sortMenuExpanded: Boolean = false,
)
