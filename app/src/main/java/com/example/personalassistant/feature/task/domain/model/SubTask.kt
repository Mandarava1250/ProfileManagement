package com.example.personalassistant.feature.task.domain.model

/**
 * 子任务 / 清单项（Domain 模型）
 *
 * - parentTaskId 非空：归属于某个 Task 的清单项
 * - parentTaskId 为空：独立清单项（即"无 Task 的纯清单模式"）
 */
data class SubTask(
    val id: Long = 0L,
    val parentTaskId: Long?,
    val title: String,
    val isCompleted: Boolean = false,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
