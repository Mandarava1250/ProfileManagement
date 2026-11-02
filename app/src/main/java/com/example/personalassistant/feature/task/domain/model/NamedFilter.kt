package com.example.personalassistant.feature.task.domain.model

import kotlinx.serialization.Serializable

/**
 * Phase 19：命名过滤器（保存的筛选条件组合）
 *
 * 滴答的「命名过滤器」= 用户把一组筛选条件存起来，起个名字，随时一键套用。
 *
 * ## 为什么存 DataStore 而不是 Room
 *
 * - 它是**用户偏好**（像「视图偏好」「象限别名」），不是业务数据
 * - 数量级小（几十条），JSON 塞进 Preferences 足够
 * - 走 Room 就要改 schema → 改迁移 → 改备份表数 → 改 schema 快照，
 *   链条长且每一步都有回归风险；收益却不匹配
 */
@Serializable
data class NamedFilter(
    /** 用户起的名字（同一份命名过滤器里唯一） */
    val name: String,
    /** 筛选条件（[FilterExpression] 的序列化形式） */
    val expression: FilterExpressionSpec,
    /** 图标标识（可选） */
    val iconKey: String = "",
    /** 排序权重（越小越靠前） */
    val sortOrder: Int = 0,
)

/**
 * [FilterExpression] 的可序列化形式。
 *
 * [FilterExpression] 本身是 sealed class，不便直接序列化（每次加 case 都会破坏
 * 老数据的反序列化）。所以这里定义**平铺的 DTO**，再用显式映射双向转换：
 *
 * - 序列化时：表达式 → DTO（只留 case 名 + 关键参数）
 * - 反序列化时：DTO → 表达式（不认识 / 参数不对的 case 一律降级为 [All]，
 *   **不抛异常** —— 老版本存的数据不该让新版本崩掉）
 */
@Serializable
data class FilterExpressionSpec(
    /** case 名（与 [FilterExpression] 的子类型名一致） */
    val type: String,
    /** 分类名（[FilterExpression.ByCategory] 用） */
    val categoryName: String? = null,
    /** 标签 id（[FilterExpression.ByTag] 用） */
    val tagId: Long? = null,
    /** 四象限名（[FilterExpression.ByQuadrant] 用） */
    val quadrantName: String? = null,
    /** 左子树（复合 case 用） */
    val left: FilterExpressionSpec? = null,
    /** 右子树（复合 case 用） */
    val right: FilterExpressionSpec? = null,
    /** 内层（[FilterExpression.Not] 用） */
    val inner: FilterExpressionSpec? = null,
) {
    companion object {
        const val TYPE_ALL = "All"
        const val TYPE_BY_CATEGORY = "ByCategory"
        const val TYPE_BY_QUADRANT = "ByQuadrant"
        const val TYPE_BY_TAG = "ByTag"
        const val TYPE_ACTIVE_ONLY = "ActiveOnly"
        const val TYPE_COMPLETED_ONLY = "CompletedOnly"
        const val TYPE_AND = "And"
        const val TYPE_OR = "Or"
        const val TYPE_NOT = "Not"
    }
}

/**
 * [FilterExpression] → [FilterExpressionSpec]
 */
fun FilterExpression.toSpec(): FilterExpressionSpec = when (this) {
    FilterExpression.All -> FilterExpressionSpec(FilterExpressionSpec.TYPE_ALL)
    FilterExpression.ActiveOnly ->
        FilterExpressionSpec(FilterExpressionSpec.TYPE_ACTIVE_ONLY)
    FilterExpression.CompletedOnly ->
        FilterExpressionSpec(FilterExpressionSpec.TYPE_COMPLETED_ONLY)
    is FilterExpression.ByCategory ->
        FilterExpressionSpec(FilterExpressionSpec.TYPE_BY_CATEGORY, categoryName = categoryName)
    is FilterExpression.ByTag ->
        FilterExpressionSpec(FilterExpressionSpec.TYPE_BY_TAG, tagId = tagId)
    is FilterExpression.ByQuadrant ->
        FilterExpressionSpec(FilterExpressionSpec.TYPE_BY_QUADRANT, quadrantName = quadrant.name)
    is FilterExpression.And ->
        FilterExpressionSpec(
            FilterExpressionSpec.TYPE_AND,
            left = left.toSpec(),
            right = right.toSpec(),
        )
    is FilterExpression.Or ->
        FilterExpressionSpec(
            FilterExpressionSpec.TYPE_OR,
            left = left.toSpec(),
            right = right.toSpec(),
        )
    is FilterExpression.Not ->
        FilterExpressionSpec(
            FilterExpressionSpec.TYPE_NOT,
            inner = inner.toSpec(),
        )
}

/**
 * [FilterExpressionSpec] → [FilterExpression]
 *
 * **防御性**：任何不认识 / 参数缺失的组合都降级为 [FilterExpression.All]，
 * 绝不抛异常 —— 老版本存的数据不该让新版本崩掉。
 */
fun FilterExpressionSpec.toExpression(): FilterExpression = when (type) {
    FilterExpressionSpec.TYPE_ALL -> FilterExpression.All
    FilterExpressionSpec.TYPE_ACTIVE_ONLY -> FilterExpression.ActiveOnly
    FilterExpressionSpec.TYPE_COMPLETED_ONLY -> FilterExpression.CompletedOnly
    FilterExpressionSpec.TYPE_BY_CATEGORY -> categoryName
        ?.let { FilterExpression.ByCategory(it) }
        ?: FilterExpression.All
    FilterExpressionSpec.TYPE_BY_TAG -> tagId
        ?.let { FilterExpression.ByTag(it) }
        ?: FilterExpression.All
    FilterExpressionSpec.TYPE_BY_QUADRANT -> quadrantName
        ?.let { name -> TaskQuadrant.entries.firstOrNull { it.name == name } }
        ?.let { FilterExpression.ByQuadrant(it) }
        ?: FilterExpression.All
    FilterExpressionSpec.TYPE_AND -> {
        val l = left?.toExpression() ?: FilterExpression.All
        val r = right?.toExpression() ?: FilterExpression.All
        FilterExpression.And(l, r)
    }
    FilterExpressionSpec.TYPE_OR -> {
        val l = left?.toExpression() ?: FilterExpression.All
        val r = right?.toExpression() ?: FilterExpression.All
        FilterExpression.Or(l, r)
    }
    FilterExpressionSpec.TYPE_NOT -> FilterExpression.Not(
        inner?.toExpression() ?: FilterExpression.All,
    )
    // 不认识的 case → 降级为 All
    else -> FilterExpression.All
}
