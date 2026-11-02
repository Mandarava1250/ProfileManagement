package com.example.personalassistant.feature.task.domain.model

/**
 * Phase 19：过滤器 DSL
 *
 * [FilterExpression] 是**树**，能表达任意组合，但手写嵌套很难读：
 * ```kotlin
 * FilterExpression.And(
 *     FilterExpression.ByTag(3L),
 *     FilterExpression.Not(FilterExpression.CompletedOnly),
 * )
 * ```
 *
 * DSL 把它变成读起来像一句话：
 * ```kotlin
 * filter {
 *     tag(3L)
 *     not(completed)
 * }
 * ```
 *
 * ## 为什么是 domain 层
 *
 * 过滤器是纯数据（不碰 IO），DSL 也就是纯语法糖 ——
 * 放在 domain 层可以直接单测，不用起数据库。
 */
class FilterDsl {

    private val conditions = mutableListOf<FilterExpression>()

    /** 分类匹配（大小写不敏感，与 [FilterExpression.ByCategory] 一致） */
    fun category(name: String) {
        conditions += FilterExpression.ByCategory(name)
    }

    /** 标签匹配 */
    fun tag(tagId: Long) {
        conditions += FilterExpression.ByTag(tagId)
    }

    /** 四象限匹配 */
    fun quadrant(quadrant: TaskQuadrant) {
        conditions += FilterExpression.ByQuadrant(quadrant)
    }

    /** 不限（显式写出来也没关系） */
    fun all() {
        conditions += FilterExpression.All
    }

    /** 仅未完成 */
    fun active() {
        conditions += FilterExpression.ActiveOnly
    }

    /** 仅已完成 */
    fun completed() {
        conditions += FilterExpression.CompletedOnly
    }

    /** 取反 */
    fun not(inner: FilterExpression) {
        conditions += FilterExpression.Not(inner)
    }

    /** 嵌套一组条件（组内 AND） */
    fun all(inner: FilterDsl.() -> Unit) {
        conditions += FilterDsl().apply(inner).build()
    }

    /** 嵌套一组条件（组内 OR） */
    fun any(inner: FilterDsl.() -> Unit) {
        conditions += FilterDsl().apply(inner).buildOr()
    }

    /** 组合两个表达式（AND） */
    infix fun FilterExpression.and(other: FilterExpression) {
        conditions += FilterExpression.And(this, other)
    }

    /** 组合两个表达式（OR） */
    infix fun FilterExpression.or(other: FilterExpression) {
        conditions += FilterExpression.Or(this, other)
    }

    /**
     * 合成最终表达式。
     *
     * 多个条件之间是 **AND**；只有一个条件时直接返回它（不套一层多余的 And）。
     */
    fun build(): FilterExpression = when (conditions.size) {
        0 -> FilterExpression.All
        1 -> conditions.first()
        else -> conditions.reduce { acc, next -> FilterExpression.And(acc, next) }
    }

    /** 合成最终表达式（组内 OR） */
    fun buildOr(): FilterExpression = when (conditions.size) {
        0 -> FilterExpression.All
        1 -> conditions.first()
        else -> conditions.reduce { acc, next -> FilterExpression.Or(acc, next) }
    }
}

/**
 * 过滤器 DSL 入口。
 *
 * ```kotlin
 * val f = filter {
 *     tag(3L)
 *     not(FilterExpression.CompletedOnly)
 * }
 * ```
 */
fun filter(block: FilterDsl.() -> Unit): FilterExpression =
    FilterDsl().apply(block).build()
