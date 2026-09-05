package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.TaskGroups

/**
 * 任务主页 UI 状态
 *
 * - isLoading：初始状态（DB 还没回值）
 * - groups：P1-P4 + 未排期 + 已完成 分组结果
 * - showCompleted：是否展开底部的"已完成"折叠区
 */
data class TaskUiState(
    val isLoading: Boolean = true,
    val groups: TaskGroups = TaskGroups(emptyMap(), emptyList()),
    val showCompleted: Boolean = false,
)
