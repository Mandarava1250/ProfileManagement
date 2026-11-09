package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 19：过滤器 DSL 单测
 */
class FilterDslTest {

    @Test
    fun `空 DSL 产出 All`() {
        assertEquals(FilterExpression.All, filter { })
    }

    @Test
    fun `单个条件不套多余的 And`() {
        val f = filter { tag(3L) }
        assertEquals(FilterExpression.ByTag(3L), f)
    }

    @Test
    fun `多个条件之间是 AND`() {
        val f = filter {
            tag(3L)
            category("工作")
        }
        assertEquals(
            FilterExpression.And(
                FilterExpression.ByTag(3L),
                FilterExpression.ByCategory("工作"),
            ),
            f,
        )
    }

    @Test
    fun `三个条件左折叠`() {
        val f = filter {
            tag(1L)
            tag(2L)
            tag(3L)
        }
        assertTrue(f is FilterExpression.And)
        val and = f as FilterExpression.And
        assertEquals(
            FilterExpression.And(
                FilterExpression.ByTag(1L),
                FilterExpression.ByTag(2L),
            ),
            and.left,
        )
        assertEquals(FilterExpression.ByTag(3L), and.right)
    }

    @Test
    fun `all 分组内部也是 AND`() {
        val f = filter {
            all {
                tag(1L)
                tag(2L)
            }
        }
        assertEquals(
            FilterExpression.And(
                FilterExpression.ByTag(1L),
                FilterExpression.ByTag(2L),
            ),
            f,
        )
    }

    @Test
    fun `any 分组内部是 OR`() {
        val f = filter {
            any {
                tag(1L)
                tag(2L)
            }
        }
        assertEquals(
            FilterExpression.Or(
                FilterExpression.ByTag(1L),
                FilterExpression.ByTag(2L),
            ),
            f,
        )
    }

    @Test
    fun `not 包装取反`() {
        val f = filter {
            not(FilterExpression.CompletedOnly)
        }
        assertEquals(
            FilterExpression.Not(FilterExpression.CompletedOnly),
            f,
        )
    }

    @Test
    fun `infix and 组合两个表达式`() {
        val f = filter {
            FilterExpression.ByTag(1L) and FilterExpression.ByCategory("x")
        }
        assertEquals(
            FilterExpression.And(
                FilterExpression.ByTag(1L),
                FilterExpression.ByCategory("x"),
            ),
            f,
        )
    }

    @Test
    fun `infix or 组合两个表达式`() {
        val f = filter {
            FilterExpression.ByTag(1L) or FilterExpression.ByTag(2L)
        }
        assertEquals(
            FilterExpression.Or(
                FilterExpression.ByTag(1L),
                FilterExpression.ByTag(2L),
            ),
            f,
        )
    }

    @Test
    fun `quadrant 与 completed 快捷方法`() {
        val f = filter {
            quadrant(TaskQuadrant.P1)
            completed()
        }
        assertEquals(
            FilterExpression.And(
                FilterExpression.ByQuadrant(TaskQuadrant.P1),
                FilterExpression.CompletedOnly,
            ),
            f,
        )
    }
}
