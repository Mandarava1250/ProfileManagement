package com.example.personalassistant.feature.task.domain.model

/**
 * 子任务 / 清单项（Domain 模型）
 *
 * - parentTaskId 非空：归属于某个 Task 的清单项
 * - parentTaskId 为空：独立清单项（即"无 Task 的纯清单模式"）
 *
 * ## Phase 15（v13）：清单项扩展字段
 *
 * - category：分类名（[TaskCategory.PRESETS] 里的名字，如「工作」）
 * - dueDate：截止时间（毫秒时间戳）
 * - priority：优先级（[TaskPriority]，DB 里存 dbValue）
 * - amount：金额（经济程度）
 * - parentSubTaskId：嵌套父清单项 id（null = 顶层清单项）
 */
data class SubTask(
    val id: Long = 0L,
    val parentTaskId: Long?,
    val title: String,
    val isCompleted: Boolean = false,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 分类名。null = 未分类 */
    val category: String? = null,
    /** 截止时间（毫秒时间戳）。null = 无截止 */
    val dueDate: Long? = null,
    /** 优先级。默认中优先级（与任务域、DB 默认值一致） */
    val priority: TaskPriority = TaskPriority.MEDIUM,
    /** 金额。null = 无金额 */
    val amount: Double? = null,
    /** 父清单项 id（自引用）。null = 顶层清单项 */
    val parentSubTaskId: Long? = null,
)
