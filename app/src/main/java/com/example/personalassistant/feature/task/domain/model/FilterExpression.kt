package com.example.personalassistant.feature.task.domain.model

/**
 * Phase 6c：任务筛选表达式（可组合的过滤树）
 *
 * ## 演化动机
 * - Phase 4a 的 [TaskFilter] 已经为扩展性做了 sealed class 设计（5 个 case）
 * - Phase 6c 需要新增 [ByTag] + 复合能力（[And] / [Or] / [Not]）
 *   - 简单做法：直接给 [TaskFilter] 加 case + 复合 case
 *   - 涟宝选了：保留旧类型不动 + 新增独立的 FilterExpression，双轨制不破坏现有调用方
 *
 * ## 双轨制策略
 * - **旧路径**（[TaskFilter]）保留：不删不重构，老 ViewModel / UseCase / Test 全部继续工作
 * - **新路径**（[FilterExpression]）启用：详情页 + 列表页 tag 筛选 + 未来组合筛选走这里
 *
 * ## 数据层
 * - Repository 同时提供 `observeTasks(filter: TaskFilter, sort)` 和 `observeTasks(filter: FilterExpression, sort)`
 * - 旧 API 内部委托给新 API（通过 [TaskFilter.toExpression] 转译），无重复逻辑
 * - 新 UI 代码（[com.example.personalassistant.feature.task.presentation.list.TaskViewModel] / TagFilterBar）直接使用 [FilterExpression]
 *
 * ## 为什么不用 sealed class 数据类（`And`/`Or`/`Not` 嵌套）
 * - Phase 4a 的 [TaskFilter] 不支持复合 case
 * - 升级它就会破坏现有调用方的 `when` 编译完整性
 * - 双轨制让两个 sealed class 各自独立演化，测试时也能单独覆盖
 */
sealed class FilterExpression {

    /** 不过所有任务（与 [TaskFilter.All] 等价） */
    data object All : FilterExpression()

    /** 按分类名筛选（与 [TaskFilter.ByCategory] 等价） */
    data class ByCategory(val categoryName: String) : FilterExpression()

    /** 按四象限筛选（与 [TaskFilter.ByQuadrant] 等价） */
    data class ByQuadrant(val quadrant: TaskQuadrant) : FilterExpression()

    /**
     * Phase 6c：按标签 id 筛选。
     *
     * 仅匹配关联表里有此 tagId 的任务。需要 Repository 层 JOIN 查询。
     */
    data class ByTag(val tagId: Long) : FilterExpression()

    /** 仅未完成（与 [TaskFilter.ActiveOnly] 等价） */
    data object ActiveOnly : FilterExpression()

    /** 仅已完成（与 [TaskFilter.CompletedOnly] 等价） */
    data object CompletedOnly : FilterExpression()

    // ===== 组合 case（Phase 6c 新增） =====

    /** 两两个条件都满足（AND） */
    data class And(val left: FilterExpression, val right: FilterExpression) : FilterExpression()

    /** 两两个条件任一满足（OR） */
    data class Or(val left: FilterExpression, val right: FilterExpression) : FilterExpression()

    /** 取反（NOT inner） */
    data class Not(val inner: FilterExpression) : FilterExpression()

    companion object {
        /** 默认筛选：仅未完成（与 [TaskFilter.ActiveOnly] 等价） */
        val DEFAULT: FilterExpression = ActiveOnly
    }
}

/**
 * Phase 6c：内部评估 [FilterExpression] 树的纯函数。
 *
 * 放在 domain/model 包，测试可直接访问（internal 在单 module 项目里 = public）。
 *
 * @param task 要评估的任务
 * @param todayEndMillis "今天结束"边界，供 [FilterExpression.ByQuadrant] 用
 * @param tagIndex 全量 tagId → 关联 taskId 集合索引；ByTag 路径用；其他 case 可传空 map
 */
internal fun FilterExpression.matches(
    task: Task,
    todayEndMillis: Long,
    tagIndex: Map<Long, Set<Long>> = emptyMap(),
): Boolean = when (this) {
    FilterExpression.All,
    FilterExpression.ActiveOnly,
    FilterExpression.CompletedOnly -> true
    is FilterExpression.ByCategory -> task.category.equals(categoryName, ignoreCase = true)
    is FilterExpression.ByQuadrant -> {
        com.example.personalassistant.feature.task.domain.usecase.ObserveTasksByQuadrantUseCase
            .classifyOne(task, todayEndMillis) == quadrant
    }
    is FilterExpression.ByTag -> tagIndex[tagId]?.contains(task.id) == true
    is FilterExpression.And -> left.matches(task, todayEndMillis, tagIndex) &&
        right.matches(task, todayEndMillis, tagIndex)
    is FilterExpression.Or -> left.matches(task, todayEndMillis, tagIndex) ||
        right.matches(task, todayEndMillis, tagIndex)
    is FilterExpression.Not -> !inner.matches(task, todayEndMillis, tagIndex)
}