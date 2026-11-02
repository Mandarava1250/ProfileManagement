package com.example.personalassistant.feature.task.domain.model

import com.example.personalassistant.feature.task.domain.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 19：命名过滤器在清单页的匹配 + 翻译
 */
class ChecklistExpressionMatcherTest {

    private val todayEnd = 1_700_000_000_000L

    private fun task(
        id: Long = 1L,
        category: String? = null,
        isCompleted: Boolean = false,
    ) = Task(
        id = id,
        title = "任务 $id",
        category = category,
        isCompleted = isCompleted,
    )

    private fun item(
        task: Task = task(),
        tags: List<Tag> = emptyList(),
    ) = ChecklistItem(task = task, tags = tags, children = emptyList())

    // ===== matchesItem =====

    @Test
    fun `All 匹配所有项`() {
        assertTrue(FilterExpression.All.matchesItem(item(), todayEnd))
        assertTrue(
            FilterExpression.All.matchesItem(
                item(task(isCompleted = true)),
                todayEnd,
            ),
        )
    }

    @Test
    fun `ActiveOnly 只匹配未完成`() {
        assertTrue(FilterExpression.ActiveOnly.matchesItem(item(), todayEnd))
        assertFalse(
            FilterExpression.ActiveOnly.matchesItem(
                item(task(isCompleted = true)),
                todayEnd,
            ),
        )
    }

    @Test
    fun `CompletedOnly 只匹配已完成`() {
        assertFalse(FilterExpression.CompletedOnly.matchesItem(item(), todayEnd))
        assertTrue(
            FilterExpression.CompletedOnly.matchesItem(
                item(task(isCompleted = true)),
                todayEnd,
            ),
        )
    }

    @Test
    fun `ByCategory 大小写不敏感`() {
        val expr = FilterExpression.ByCategory("学习")
        assertTrue(expr.matchesItem(item(task(category = "学习")), todayEnd))
        assertTrue(expr.matchesItem(item(task(category = "学习")), todayEnd))
        assertFalse(expr.matchesItem(item(task(category = "生活")), todayEnd))
    }

    @Test
    fun `ByTag 按标签 id 匹配`() {
        val expr = FilterExpression.ByTag(7L)
        val tagged = item(
            tags = listOf(
                Tag(
                    id = 7L,
                    nameNormalized = "important",
                    displayName = "重要",
                    colorHex = 0xFF000000,
                ),
            ),
        )
        assertTrue(expr.matchesItem(tagged, todayEnd))
        assertFalse(expr.matchesItem(item(), todayEnd))
    }

    @Test
    fun `And 要求两边都满足`() {
        val expr = FilterExpression.And(
            FilterExpression.ByCategory("学习"),
            FilterExpression.ActiveOnly,
        )
        assertTrue(expr.matchesItem(item(task(category = "学习")), todayEnd))
        assertFalse(
            expr.matchesItem(item(task(category = "学习", isCompleted = true)), todayEnd),
        )
        assertFalse(expr.matchesItem(item(task(category = "生活")), todayEnd))
    }

    @Test
    fun `Or 任一满足即可`() {
        val expr = FilterExpression.Or(
            FilterExpression.ByCategory("学习"),
            FilterExpression.ByCategory("生活"),
        )
        assertTrue(expr.matchesItem(item(task(category = "学习")), todayEnd))
        assertTrue(expr.matchesItem(item(task(category = "生活")), todayEnd))
        assertFalse(expr.matchesItem(item(task(category = "工作")), todayEnd))
    }

    @Test
    fun `Not 取反`() {
        val expr = FilterExpression.Not(FilterExpression.CompletedOnly)
        assertTrue(expr.matchesItem(item(), todayEnd))
        assertFalse(expr.matchesItem(item(task(isCompleted = true)), todayEnd))
    }

    // ===== toExpression =====

    @Test
    fun `空筛选翻译成 All`() {
        assertEquals(
            FilterExpression.All,
            ChecklistFilter().toExpression(),
        )
    }

    @Test
    fun `分类翻译成 ByCategory`() {
        assertEquals(
            FilterExpression.ByCategory("学习"),
            ChecklistFilter(category = "学习").toExpression(),
        )
    }

    @Test
    fun `空白分类不算条件`() {
        assertEquals(
            FilterExpression.All,
            ChecklistFilter(category = "   ").toExpression(),
        )
    }

    @Test
    fun `标签翻译成 ByTag`() {
        assertEquals(
            FilterExpression.ByTag(3L),
            ChecklistFilter(tagId = 3L).toExpression(),
        )
    }

    @Test
    fun `已完成分组翻译成 CompletedOnly`() {
        assertEquals(
            FilterExpression.CompletedOnly,
            ChecklistFilter(group = ChecklistGroup.COMPLETED).toExpression(),
        )
    }

    @Test
    fun `未完成分组翻译成 ActiveOnly`() {
        assertEquals(
            FilterExpression.ActiveOnly,
            ChecklistFilter(group = ChecklistGroup.UNCOMPLETED).toExpression(),
        )
    }

    @Test
    fun `排布型分组不参与翻译`() {
        assertEquals(
            FilterExpression.All,
            ChecklistFilter(group = ChecklistGroup.BY_DUE_DATE).toExpression(),
        )
        assertEquals(
            FilterExpression.All,
            ChecklistFilter(group = ChecklistGroup.BY_CATEGORY).toExpression(),
        )
    }

    @Test
    fun `分类和标签同时存在时是 AND`() {
        assertEquals(
            FilterExpression.And(
                FilterExpression.ByCategory("学习"),
                FilterExpression.ByTag(3L),
            ),
            ChecklistFilter(category = "学习", tagId = 3L).toExpression(),
        )
    }

    @Test
    fun `排序不参与翻译`() {
        assertEquals(
            FilterExpression.All,
            ChecklistFilter(sort = ChecklistSort.DUE_DATE).toExpression(),
        )
    }
}
