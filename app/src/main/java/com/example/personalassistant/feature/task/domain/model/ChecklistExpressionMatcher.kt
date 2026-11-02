package com.example.personalassistant.feature.task.domain.model

import com.example.personalassistant.feature.task.domain.usecase.ObserveTasksByQuadrantUseCase

/**
 * Phase 19：[FilterExpression] 对**清单项**的求值（纯函数）
 *
 * ## 为什么不复用 [FilterExpression.matches]
 *
 * 任务侧的 [FilterExpression.matches] 把「仅未完成 / 仅已完成」交给 SQL 层做
 * （`dao.observeActive()` / `dao.observeCompleted()`），所以它对这两个 case 一律返回 true。
 *
 * 清单页是**内存里**过滤（[ChecklistFilter.applyTo]），没有 SQL 层兜底 ——
 * 这里必须自己判完成状态，否则命名过滤器「仅未完成」会失效。
 *
 * 所以两边语义故意不同，函数名也不同（[matchesItem] vs [matches]）。
 */
internal fun FilterExpression.matchesItem(
    item: ChecklistItem,
    todayEndMillis: Long,
): Boolean = when (this) {
    FilterExpression.All -> true
    FilterExpression.ActiveOnly -> !item.task.isCompleted
    FilterExpression.CompletedOnly -> item.task.isCompleted
    is FilterExpression.ByCategory ->
        item.task.category.equals(categoryName, ignoreCase = true)
    is FilterExpression.ByTag -> item.tags.any { it.id == tagId }
    is FilterExpression.ByQuadrant ->
        ObserveTasksByQuadrantUseCase.classifyOne(item.task, todayEndMillis) == quadrant
    is FilterExpression.And ->
        left.matchesItem(item, todayEndMillis) && right.matchesItem(item, todayEndMillis)
    is FilterExpression.Or ->
        left.matchesItem(item, todayEndMillis) || right.matchesItem(item, todayEndMillis)
    is FilterExpression.Not -> !inner.matchesItem(item, todayEndMillis)
}

/**
 * Phase 19：把清单页当前的筛选条件翻译成 [FilterExpression]
 *
 * 用于「把当前筛选存成命名过滤器」。
 *
 * ## 翻译规则
 *
 * - 分类 → [FilterExpression.ByCategory]
 * - 标签 → [FilterExpression.ByTag]
 * - 分组 `COMPLETED`（只看已完成）→ [FilterExpression.CompletedOnly]
 * - 分组 `UNCOMPLETED`（未完成 / 已完成两组）→ [FilterExpression.ActiveOnly]
 *   （分组本身是**排布**，但落到命名过滤器里表达的是「只关心未完成的那批」）
 * - 其它分组（NONE / BY_CATEGORY / BY_DUE_DATE）→ 不参与翻译
 *   —— 那是排布维度，不是「哪些项符合条件」
 * - 排序 → **不翻译**：排序不是筛选条件
 *
 * 多个条件之间是 AND。
 */
internal fun ChecklistFilter.toExpression(): FilterExpression {
    val conditions = mutableListOf<FilterExpression>()

    category?.takeIf { it.isNotBlank() }?.let {
        conditions += FilterExpression.ByCategory(it)
    }
    tagId?.let {
        conditions += FilterExpression.ByTag(it)
    }
    when (group) {
        ChecklistGroup.COMPLETED -> conditions += FilterExpression.CompletedOnly
        ChecklistGroup.UNCOMPLETED -> conditions += FilterExpression.ActiveOnly
        else -> Unit
    }

    return when (conditions.size) {
        0 -> FilterExpression.All
        1 -> conditions.first()
        else -> conditions.reduce { acc, next -> FilterExpression.And(acc, next) }
    }
}
