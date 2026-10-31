package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.ChecklistItem
import com.example.personalassistant.feature.task.domain.model.ChecklistSection
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.TaskPriority

/**
 * Phase 15：清单项的 UI 模型（把 Domain 的组合模型摊平，方便直接渲染）
 *
 * 与 [ChecklistItem] 的区别：这里没有 `subTask` 这一层嵌套，
 * 直接就是 UI 要用的字段（标题 / 完成态 / 分类 / 截止 / 优先级 / 金额 / 标签 / 子项）。
 */
data class ChecklistItemUi(
    val id: Long,
    val title: String,
    val isCompleted: Boolean,
    val order: Int,
    /** 分类名。null = 未分类 */
    val category: String? = null,
    /** 截止时间（毫秒时间戳）。null = 无截止 */
    val dueDate: Long? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    /** 金额。null = 无金额 */
    val amount: Double? = null,
    /** 父清单项 id。null = 顶层项 */
    val parentSubTaskId: Long? = null,
    /** 关联标签 */
    val tags: List<Tag> = emptyList(),
    /** 嵌套子清单项 */
    val children: List<ChecklistItemUi> = emptyList(),
)

/**
 * Phase 15：清单分组的一节（UI 模型）
 *
 * @param key 分组标识（稳定值，可用作 lazy key / 折叠状态 key）
 * @param title 分组展示标题
 * @param items 该组内的清单项
 */
data class ChecklistSectionUi(
    val key: String,
    val title: String,
    val items: List<ChecklistItemUi>,
)

/** Domain 清单项 → UI 模型 */
internal fun ChecklistItem.toUi(): ChecklistItemUi = ChecklistItemUi(
    id = subTask.id,
    title = subTask.title,
    isCompleted = subTask.isCompleted,
    order = subTask.order,
    category = subTask.category,
    dueDate = subTask.dueDate,
    priority = subTask.priority,
    amount = subTask.amount,
    parentSubTaskId = subTask.parentSubTaskId,
    tags = tags,
    children = children.map { it.toUi() },
)

/** Domain 分组 → UI 模型 */
internal fun ChecklistSection.toUi(): ChecklistSectionUi = ChecklistSectionUi(
    key = key,
    title = title,
    items = items.map { it.toUi() },
)
