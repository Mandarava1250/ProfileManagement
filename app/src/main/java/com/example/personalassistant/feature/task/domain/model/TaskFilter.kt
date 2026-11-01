package com.example.personalassistant.feature.task.domain.model

/**
 * 任务筛选条件（首页用）。
 *
 * 用 sealed class 是为了未来扩展新筛选维度（标签、状态、日期范围）时不破坏现有调用方。
 *
 * Phase 4a 设计取舍：
 * - [ByCategory] 用 String（category 名）而不是 Long id —— 因为 [Task.category] 本身就是 String?，
 *   现有 DB schema（v2）已落库 category 文本。Phase 5 若要 Category 表外键，再升级到 Long id。
 *
 * Phase 6c 演化（双轨制）：
 * - 旧 [TaskFilter] 不删，继续作为「TaskFilterBar / 老 ViewModel」的入参类型
 * - 新代码统一用 [FilterExpression]（支持 [FilterExpression.ByTag] + 复合 case）
 * - [toExpression] 提供单向转译，把旧 case 转成新 case
 * - 这样保留了所有现有调用方（Tests / VM / UseCase）零修改
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

/**
 * Phase 6c：把 [TaskFilter] 转译成 [FilterExpression]（一对一映射）。
 *
 * 用于 Repository 旧入口的内部委托：
 * `observeTasks(filter: TaskFilter, sort)` -> `observeTasks(filter.toExpression(), sort)`
 *
 * 所有 case 都有一一对应：[All]->[All]、[ActiveOnly]->[ActiveOnly] 等等。
 */
internal fun TaskFilter.toExpression(): FilterExpression = when (this) {
    TaskFilter.All -> FilterExpression.All
    is TaskFilter.ByCategory -> FilterExpression.ByCategory(categoryName)
    is TaskFilter.ByQuadrant -> FilterExpression.ByQuadrant(quadrant)
    TaskFilter.ActiveOnly -> FilterExpression.ActiveOnly
    TaskFilter.CompletedOnly -> FilterExpression.CompletedOnly
}