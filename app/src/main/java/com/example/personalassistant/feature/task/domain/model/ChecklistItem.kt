package com.example.personalassistant.feature.task.domain.model

/**
 * Phase 15：清单项（Domain 组合模型）
 *
 * 单纯的 [SubTask] 只带自身字段，清单页需要「一项 + 它的标签 + 它的子清单」，
 * 所以数据层读出来之后组合成这个模型交给上层。
 *
 * - subTask：清单项本体（含分类 / 截止时间 / 优先级 / 金额）
 * - tags：关联标签（DB 表 `sub_task_tag_cross_ref`）
 * - children：嵌套子清单项（`parentSubTaskId` 指向本项）
 */
data class ChecklistItem(
    val subTask: SubTask,
    val tags: List<Tag> = emptyList(),
    val children: List<ChecklistItem> = emptyList(),
) {
    /** 清单项 id（快捷访问，省得 UI 每次都写 `item.subTask.id`） */
    val id: Long get() = subTask.id

    /** 标题 */
    val title: String get() = subTask.title

    /** 是否已完成 */
    val isCompleted: Boolean get() = subTask.isCompleted

    /** 分类名。null = 未分类 */
    val category: String? get() = subTask.category
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
