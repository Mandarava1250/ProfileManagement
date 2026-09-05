package com.example.personalassistant.feature.task.domain.model

/**
 * 任务（Domain 模型）
 *
 * 字段含义对齐 TaskEntity，但把 priority 和 repeatType 提升为强类型枚举，
 * 业务层不再与 Int/String 直接打交道。
 */
data class Task(
    val id: Long = 0L,
    val title: String,
    val description: String? = null,
    val dueDate: Long? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val isCompleted: Boolean = false,
    val category: String? = null,
    val reminderTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatEndDate: Long? = null,
)
