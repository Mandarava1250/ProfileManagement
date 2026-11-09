package com.example.personalassistant.feature.task.domain.model

/**
 * 任务 + 子任务组合（视图模型）
 *
 * 编辑页使用：加载任务后连同其下所有子任务一起呈现。
 *
 * ## Phase 16
 *
 * 原来 `subTasks: List<Task>` —— 现在子任务也是 [Task]（同一张表），
 * 所以类型变成 `List<Task>`。
 */
data class TaskWithSubTasks(
    val task: Task,
    val subTasks: List<Task>,
)
