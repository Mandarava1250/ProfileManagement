package com.example.personalassistant.feature.task.domain.model

/**
 * 任务 + 子清单组合（视图模型）
 *
 * 编辑页使用：加载任务后连同其下所有子清单一起呈现。
 */
data class TaskWithSubTasks(
    val task: Task,
    val subTasks: List<SubTask>,
)
