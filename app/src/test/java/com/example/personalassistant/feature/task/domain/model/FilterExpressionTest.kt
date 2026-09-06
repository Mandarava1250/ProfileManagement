package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6c：[FilterExpression] 评估测试。
 *
 * 覆盖：
 * 1. 每个叶子 case 的评估逻辑（All / ByCategory / ByQuadrant / ActiveOnly / CompletedOnly / ByTag）
 * 2. 复合 case 的布尔代数（And / Or / Not）
 * 3. 嵌套树（And(And(a,b), Or(c, Not(d)))）的求值正确性
 * 4. [TaskFilter.toExpression] 一对一映射（旧类型转新类型无丢失）
 *
 * 故意不依赖任何 Room / coroutines，纯 sealed class 树评估。
 */
class FilterExpressionTest {

    private val todayEnd = 1_700_000_000_000L

    private fun task(
        id: Long = 0L,
        title: String = "t$id",
        category: String? = null,
        dueDate: Long? = null,
        priority: TaskPriority = TaskPriority.MEDIUM,
        isCompleted: Boolean = false,
    ) = Task(
        id = id,
        title = title,
        dueDate = dueDate,
        priority = priority,
        isCompleted = isCompleted,
        category = category,
    )

    private val tagIndex = mapOf(
        100L to setOf(1L, 2L, 3L),
        200L to setOf(2L, 4L),
    )

    // ===== 叶子 case =====

    @Test
    fun `All matches every task`() {
        val active = task(id = 1L)
        val completed = task(id = 2L, isCompleted = true)
        val unscheduled = task(id = 3L)
        assertTrue(FilterExpression.All.matches(active, todayEnd, tagIndex))
        assertTrue(FilterExpression.All.matches(completed, todayEnd, tagIndex))
        assertTrue(FilterExpression.All.matches(unscheduled, todayEnd, tagIndex))
    }

    @Test
    fun `ActiveOnly and CompletedOnly both match every task (filter only)`() {
        val t = task(id = 1L)
        assertTrue(FilterExpression.ActiveOnly.matches(t, todayEnd, tagIndex))
        assertTrue(FilterExpression.CompletedOnly.matches(t, todayEnd, tagIndex))
    }

    @Test
    fun `ByCategory matches case-insensitively`() {
        val t = task(id = 1L, category = "Study")
        assertTrue(FilterExpression.ByCategory("study").matches(t, todayEnd, tagIndex))
        assertTrue(FilterExpression.ByCategory("STUDY").matches(t, todayEnd, tagIndex))
        assertFalse(FilterExpression.ByCategory("work").matches(t, todayEnd, tagIndex))
    }

    @Test
    fun `ByCategory with null category never matches`() {
        val t = task(id = 1L, category = null)
        assertFalse(FilterExpression.ByCategory("anything").matches(t, todayEnd, tagIndex))
    }

    @Test
    fun `ByQuadrant delegates to ObserveTasksByQuadrantUseCase classifyOne`() {
        val p1 = task(id = 1L, dueDate = todayEnd - 1, priority = TaskPriority.HIGH)
        assertTrue(FilterExpression.ByQuadrant(TaskQuadrant.P1).matches(p1, todayEnd, tagIndex))
        assertFalse(FilterExpression.ByQuadrant(TaskQuadrant.P2).matches(p1, todayEnd, tagIndex))
    }

    @Test
    fun `ByTag matches only tasks associated with the tagId`() {
        val t1 = task(id = 1L)
        val t2 = task(id = 2L)
        val t3 = task(id = 3L)
        val t4 = task(id = 4L)
        // tag 100 → tasks {1, 2, 3}
        assertTrue(FilterExpression.ByTag(100L).matches(t1, todayEnd, tagIndex))
        assertTrue(FilterExpression.ByTag(100L).matches(t2, todayEnd, tagIndex))
        assertTrue(FilterExpression.ByTag(100L).matches(t3, todayEnd, tagIndex))
        assertFalse(FilterExpression.ByTag(100L).matches(t4, todayEnd, tagIndex))
        // tag 200 → tasks {2, 4}
        assertFalse(FilterExpression.ByTag(200L).matches(t1, todayEnd, tagIndex))
        assertTrue(FilterExpression.ByTag(200L).matches(t2, todayEnd, tagIndex))
        assertFalse(FilterExpression.ByTag(200L).matches(t3, todayEnd, tagIndex))
        assertTrue(FilterExpression.ByTag(200L).matches(t4, todayEnd, tagIndex))
    }

    @Test
    fun `ByTag with non-existent tagId matches no task`() {
        val t = task(id = 1L)
        assertFalse(FilterExpression.ByTag(999L).matches(t, todayEnd, tagIndex))
    }

    // ===== 复合 case =====

    @Test
    fun `And matches only when both sides match`() {
        val matching = FilterExpression.And(FilterExpression.All, FilterExpression.All)
        val leftOnly = FilterExpression.And(FilterExpression.All, FilterExpression.ByTag(999L))
        val rightOnly = FilterExpression.And(FilterExpression.ByTag(999L), FilterExpression.All)
        val neither = FilterExpression.And(FilterExpression.ByTag(999L), FilterExpression.ByTag(999L))
        val t = task(id = 1L)
        assertTrue(matching.matches(t, todayEnd, tagIndex))
        assertFalse(leftOnly.matches(t, todayEnd, tagIndex))
        assertFalse(rightOnly.matches(t, todayEnd, tagIndex))
        assertFalse(neither.matches(t, todayEnd, tagIndex))
    }

    @Test
    fun `Or matches when at least one side matches`() {
        val leftOnly = FilterExpression.Or(FilterExpression.All, FilterExpression.ByTag(999L))
        val rightOnly = FilterExpression.Or(FilterExpression.ByTag(999L), FilterExpression.All)
        val neither = FilterExpression.Or(FilterExpression.ByTag(999L), FilterExpression.ByTag(998L))
        val t = task(id = 1L)
        assertTrue(leftOnly.matches(t, todayEnd, tagIndex))
        assertTrue(rightOnly.matches(t, todayEnd, tagIndex))
        assertFalse(neither.matches(t, todayEnd, tagIndex))
    }

    @Test
    fun `Not inverts the inner`() {
        val inverted = FilterExpression.Not(FilterExpression.ByTag(999L))
        val identity = FilterExpression.Not(FilterExpression.All)
        val t = task(id = 1L)
        // All 不匹配 ByTag(999L)，所以 Not(ByTag(999L)) = match-all
        assertTrue(inverted.matches(t, todayEnd, tagIndex))
        // Not(All) = 永远 false
        assertFalse(identity.matches(t, todayEnd, tagIndex))
    }

    @Test
    fun `nested tree And And Or Not evaluates correctly`() {
        // And(All, Or(ByTag(100L), Not(ByTag(999L))))
        // tag 100: task 1 是成员 → Or 真 → And 真
        // tag 999: 不存在 → Not(...) 真 → Or 真 → And 真
        // 但 Not(All) 永远 false → And 永远 false
        val expr = FilterExpression.And(
            FilterExpression.All,
            FilterExpression.Or(
                FilterExpression.ByTag(100L),
                FilterExpression.Not(FilterExpression.ByTag(999L))
            )
        )
        assertTrue(expr.matches(task(id = 1L), todayEnd, tagIndex))
        assertTrue(expr.matches(task(id = 5L), todayEnd, tagIndex))

        // 嵌套第二层 Not(All) → false
        val never = FilterExpression.And(
            FilterExpression.Not(FilterExpression.All),
            FilterExpression.All
        )
        assertFalse(never.matches(task(id = 1L), todayEnd, tagIndex))
    }

    // ===== TaskFilter.toExpression 单向映射 =====

    @Test
    fun `TaskFilter All maps to FilterExpression All`() {
        assertEquals(FilterExpression.All, TaskFilter.All.toExpression())
    }

    @Test
    fun `TaskFilter ActiveOnly maps to FilterExpression ActiveOnly`() {
        assertEquals(FilterExpression.ActiveOnly, TaskFilter.ActiveOnly.toExpression())
    }

    @Test
    fun `TaskFilter CompletedOnly maps to FilterExpression CompletedOnly`() {
        assertEquals(FilterExpression.CompletedOnly, TaskFilter.CompletedOnly.toExpression())
    }

    @Test
    fun `TaskFilter ByCategory maps to FilterExpression ByCategory with same name`() {
        val mapped = TaskFilter.ByCategory("study").toExpression()
        assertEquals(FilterExpression.ByCategory("study"), mapped)
    }

    @Test
    fun `TaskFilter ByQuadrant maps to FilterExpression ByQuadrant with same quadrant`() {
        val mapped = TaskFilter.ByQuadrant(TaskQuadrant.P1).toExpression()
        assertEquals(FilterExpression.ByQuadrant(TaskQuadrant.P1), mapped)
    }

    @Test
    fun `TaskFilter toExpression covers every case (exhaustive)`() {
        // 编译器：when 必须穷举 TaskFilter 所有 case（switch 该报错就报错）
        val filters: List<TaskFilter> = listOf(
            TaskFilter.All,
            TaskFilter.ActiveOnly,
            TaskFilter.CompletedOnly,
            TaskFilter.ByCategory("study"),
            TaskFilter.ByQuadrant(TaskQuadrant.P1),
        )
        filters.forEach { f ->
            // toExpression 不抛错，结果是 FilterExpression 实例
            val e = f.toExpression()
            assertTrue(e is FilterExpression)
        }
    }
}