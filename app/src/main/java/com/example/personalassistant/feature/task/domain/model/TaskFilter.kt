package com.example.personalassistant.feature.task.domain.model

/**
 * 任务筛选条件（首页用）。
 *
 * 用 sealed class 是为了未来扩展新筛选维度（标签、状态、日期范围）时不破坏现有调用方。
 *
 * Phase 4a 设计取舍：
 * - [ByCategory] 用 String（category 名）而不是 Long id —— 因为 [Task.category] 本身就是 String?，
 *   现有 DB schema（v2）已落库 category 文本。Phase 5 若要 category 表外键，再升级到 Long id。
 */
sealed class TaskFilter {
    /** 不过滤 */
    object All : TaskFilter()

    /** 按分类名筛选（与 Task.category: String? 对齐） */
    data class ByCategory(val categoryName: String) : TaskFilter()

    /** 按四象限筛选 */
    data class ByQuadrant(val quadrant: TaskQuadrant) : TaskFilter()

    /** 仅未完成 */
    object ActiveOnly : TaskFilter()

    /** 仅已完成 */
    object CompletedOnly : TaskFilter()
}
