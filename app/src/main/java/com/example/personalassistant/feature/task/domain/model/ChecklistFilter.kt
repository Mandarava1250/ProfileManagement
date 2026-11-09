package com.example.personalassistant.feature.task.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * Phase 15：清单分组方式
 *
 * - NONE：不分组（单组）
 * - UNCOMPLETED：未完成 / 已完成 两组
 * - COMPLETED：只看已完成
 * - BY_CATEGORY：按分类名分组（无分类的归到「未分类」）
 * - BY_DUE_DATE：按截止时间分组（已逾期 / 今天 / 之后 / 无截止）
 */
enum class ChecklistGroup {
    NONE,
    UNCOMPLETED,
    COMPLETED,
    BY_CATEGORY,
    BY_DUE_DATE,
}

/**
 * Phase 15：清单排序方式
 *
 * - MANUAL：手动顺序（`order` 升序，同序按创建时间升序）—— 与 DB 默认查询一致
 * - PRIORITY：优先级高 → 低（同优先级按手动顺序）
 * - DUE_DATE：截止时间早 → 晚（无截止排最后）
 * - CREATED_DESC：创建时间晚 → 早
 * - TITLE_ASC：标题 A → Z（不区分大小写）
 */
enum class ChecklistSort {
    MANUAL,
    PRIORITY,
    DUE_DATE,
    CREATED_DESC,
    TITLE_ASC,
}

/**
 * Phase 15：清单筛选条件
 *
 * - category：按分类名筛（null = 不筛）
 * - tagId：按标签 id 筛（null = 不筛）
 * - group：分组方式
 * - sort：排序方式
 *
 * 分类与标签是「与」关系：两个都设了就取交集。
 *
 * ## Phase 19：expression
 *
 * `expression` 是**命名过滤器**套用进来的复合筛选条件（[FilterExpression] 树）。
 * 它跟 category / tagId 是「与」关系 —— 套用一个命名过滤器时，
 * 分类和标签会先被翻译成表达式，再跟 expression 合并。
 *
 * 为 null = 没用命名过滤器（普通状态）。
 */
data class ChecklistFilter(
    val category: String? = null,
    val tagId: Long? = null,
    val group: ChecklistGroup = ChecklistGroup.NONE,
    val sort: ChecklistSort = ChecklistSort.MANUAL,
    val expression: FilterExpression? = null,
)

/**
 * 套用筛选 + 排序（纯函数，便于单测覆盖）。
 *
 * 顺序：先筛后排 —— 分组用的就是排好序的列表。
 *
 * @param todayEndMillis 「今天结束」边界，供四象限判定用
 */
internal fun ChecklistFilter.applyTo(
    items: List<ChecklistItem>,
    todayEndMillis: Long = dayEndMillis(),
): List<ChecklistItem> {
    val filtered = items.filter { item ->
        val categoryOk = category == null || item.task.category == category
        val tagOk = tagId == null || item.tags.any { it.id == tagId }
        // Phase 19：命名过滤器的复合条件（null = 不额外筛）
        val expressionOk = expression == null ||
            expression.matchesItem(item, todayEndMillis)
        categoryOk && tagOk && expressionOk
    }
    return sortItems(filtered)
}

/**
 * 按 [ChecklistSort] 排序（纯函数）。
 *
 * ## Phase 24：置顶优先
 *
 * 所有排序方式的第一关键字都是 `isPinned`（置顶的排最前）。
 * 必须跟 DAO 的 `ORDER BY isPinned DESC, ...` 对齐 —— 否则同一个列表
 * 在「刚进页面」和「切了筛选条件」之后会给出两种顺序。
 */
internal fun ChecklistFilter.sortItems(items: List<ChecklistItem>): List<ChecklistItem> = when (sort) {
    ChecklistSort.MANUAL -> items.sortedWith(
        compareByDescending<ChecklistItem> { it.task.isPinned }
            .thenBy { it.task.manualOrder }
            .thenBy { it.task.createdAt },
    )
    ChecklistSort.PRIORITY -> items.sortedWith(
        compareByDescending<ChecklistItem> { it.task.isPinned }
            .thenByDescending { it.task.priority.rank }
            .thenBy { it.task.manualOrder },
    )
    ChecklistSort.DUE_DATE -> items.sortedWith(
        compareByDescending<ChecklistItem> { it.task.isPinned }
            .thenBy { it.task.dueDate ?: Long.MAX_VALUE }
            .thenBy { it.task.manualOrder },
    )
    ChecklistSort.CREATED_DESC -> items.sortedWith(
        compareByDescending<ChecklistItem> { it.task.isPinned }
            .thenByDescending { it.task.createdAt },
    )
    ChecklistSort.TITLE_ASC -> items.sortedWith(
        compareByDescending<ChecklistItem> { it.task.isPinned }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.task.title },
    )
}

/**
 * 按 [ChecklistGroup] 分组（纯函数）。
 *
 * @param now 「今天」的判定基准（便于单测注入固定时间）
 */
internal fun groupChecklistItems(
    items: List<ChecklistItem>,
    group: ChecklistGroup,
    now: Long = System.currentTimeMillis(),
): List<ChecklistSection> = when (group) {
    ChecklistGroup.NONE -> listOf(ChecklistSection("all", "全部", items))
    ChecklistGroup.UNCOMPLETED -> listOf(
        ChecklistSection("uncompleted", "未完成", items.filter { !it.task.isCompleted }),
        ChecklistSection("completed", "已完成", items.filter { it.task.isCompleted }),
    )
    ChecklistGroup.COMPLETED -> listOf(
        ChecklistSection("completed", "已完成", items.filter { it.task.isCompleted }),
    )
    ChecklistGroup.BY_CATEGORY -> items
        .groupBy { it.task.category ?: UNCATEGORIZED_TITLE }
        .map { (category, groupItems) -> ChecklistSection("category:$category", category, groupItems) }
    ChecklistGroup.BY_DUE_DATE -> {
        val dayStart = dayStartMillis(now)
        val dayEnd = dayEndMillis(now)
        val overdue = items.filter { it.task.dueDate != null && it.task.dueDate < dayStart }
        val today = items.filter { it.task.dueDate != null && it.task.dueDate in dayStart..dayEnd }
        val later = items.filter { it.task.dueDate != null && it.task.dueDate > dayEnd }
        val none = items.filter { it.task.dueDate == null }
        listOf(
            ChecklistSection("overdue", "已逾期", overdue),
            ChecklistSection("today", "今天", today),
            ChecklistSection("later", "之后", later),
            ChecklistSection("noDueDate", "无截止时间", none),
        )
    }
}

/** 未分类清单项的分组标题 */
internal const val UNCATEGORIZED_TITLE = "未分类"

/** 当前系统时区的「今天 00:00」时刻（毫秒） */
internal fun dayStartMillis(now: Long = System.currentTimeMillis()): Long {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return date.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** 当前系统时区的「今天 23:59:59.999」时刻（毫秒） */
internal fun dayEndMillis(now: Long = System.currentTimeMillis()): Long {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return date.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
}
