package com.example.personalassistant.feature.task.domain.model

/**
 * Phase 15/16：清单项（Domain 组合模型）
 *
 * 单纯的 [Task] 只带自身字段，清单页需要「一项 + 它的标签 + 它的子清单」，
 * 所以数据层读出来之后组合成这个模型交给上层。
 *
 * - task：清单项本体（[Task] —— Phase 16 起任务与清单项是同一个模型）
 * - tags：关联标签（DB 表 `task_tag_cross_ref`）
 * - children：嵌套子清单项（`parentTaskId` 指向本项）
 *
 * ## Phase 16 的变化
 *
 * 原来包装的是 `SubTask`，现在包装的是 [Task] —— `sub_tasks` 表已并入 `tasks`，
 * 层级靠 `parentTaskId` 自引用表达。
 */
data class ChecklistItem(
    val task: Task,
    val tags: List<Tag> = emptyList(),
    val children: List<ChecklistItem> = emptyList(),
) {
    /** 清单项 id（快捷访问，省得 UI 每次都写 `item.task.id`） */
    val id: Long get() = task.id

    /** 标题 */
    val title: String get() = task.title

    /** 是否已完成 */
    val isCompleted: Boolean get() = task.isCompleted

    /** 分类名。null = 未分类 */
    val category: String? get() = task.category

    /** 截止时间（毫秒时间戳）。null = 无截止 */
    val dueDate: Long? get() = task.dueDate

    /** 优先级 */
    val priority: TaskPriority get() = task.priority

    /** 金额。null = 无金额 */
    val amount: Double? get() = task.amount

    /** 父清单项 id。null = 顶层清单项 */
    val parentTaskId: Long? get() = task.parentTaskId
}

/**
 * Phase 15：清单分组结果的一节
 *
 * @param key 分组标识（稳定值，UI 可用作 lazy key / 折叠状态 key）
 * @param title 分组展示标题
 * @param items 该组内的清单项（顺序 = 排序后的顺序）
 */
data class ChecklistSection(
    val key: String,
    val title: String,
    val items: List<ChecklistItem>,
)
