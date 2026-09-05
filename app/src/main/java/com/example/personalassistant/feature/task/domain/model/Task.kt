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
    /**
     * Phase 4b：自定义星期几（与 RepeatRule.CustomWeekdays 配套）。
     * 字符串格式 "MONDAY,WEDNESDAY,FRIDAY"，null = 无自定义。
     * - 当业务层持有 RepeatRule.CustomWeekdays(days) 时，转 Entity 之前会先把 days 序列化到这里
     * - 当业务层读出 RepeatRule 时，会从 repeatType + repeatDays 还原为 RepeatRule 对象
     */
    val repeatDays: String? = null,
)
