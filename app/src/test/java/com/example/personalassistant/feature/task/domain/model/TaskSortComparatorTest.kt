package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6a：TaskSort 排序行为单测（聚焦 MANUAL_ORDER + null 兜底）。
 *
 * - MANUAL_ORDER: manualOrder 升序，null 兜底 id 升序
 * - 其他 sort 仅做烟雾测试，确保新增分支没破坏现有排序
 *
 * 直接调用生产代码 `TaskSort.toComparator()`（internal 顶层 extension），
 * 测试与生产共享同一份逻辑，避免双份维护。
 */
class TaskSortComparatorTest {

    private fun task(
        id: Long,
        manualOrder: Long? = null,
        title: String = "t$id",
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueDate: Long? = null,
        createdAt: Long = id,
    ) = Task(
        id = id,
        title = title,
        priority = priority,
        dueDate = dueDate,
        createdAt = createdAt,
        manualOrder = manualOrder,
    )

    @Test
    fun `MANUAL_ORDER asc by manualOrder, null 走 id 升序兜底`() {
        val comparator = TaskSort.MANUAL_ORDER.toComparator()
        val tasks = listOf(
            task(id = 3, manualOrder = null),
            task(id = 1, manualOrder = 2),
            task(id = 2, manualOrder = 0),
            task(id = 4, manualOrder = null),
        )
        val sorted = tasks.sortedWith(comparator).map { it.id }
        // 期望顺序：manualOrder=0 (id=2) → manualOrder=2 (id=1) → null 兜底 id 升序 (3, 4)
        assertEquals(listOf(2L, 1L, 3L, 4L), sorted)
    }

    @Test
    fun `MANUAL_ORDER 全 null 时稳定按 id 升序`() {
        val comparator = TaskSort.MANUAL_ORDER.toComparator()
        val tasks = listOf(
            task(id = 5, manualOrder = null),
            task(id = 2, manualOrder = null),
            task(id = 8, manualOrder = null),
        )
        val sorted = tasks.sortedWith(comparator).map { it.id }
        assertEquals(listOf(2L, 5L, 8L), sorted)
    }

    @Test
    fun `MANUAL_ORDER 同 manualOrder 值时按 id 升序兜底（稳定排序）`() {
        val comparator = TaskSort.MANUAL_ORDER.toComparator()
        val tasks = listOf(
            task(id = 3, manualOrder = 1),
            task(id = 1, manualOrder = 1),
            task(id = 2, manualOrder = 0),
        )
        val sorted = tasks.sortedWith(comparator).map { it.id }
        // manualOrder=0 先 → 两个 =1 的按 id 升序
        assertEquals(listOf(2L, 1L, 3L), sorted)
    }

    @Test
    fun `PRIORITY_DUE_ASC 仍按优先级+截止排（不破坏现有行为）`() {
        val comparator = TaskSort.PRIORITY_DUE_ASC.toComparator()
        val tasks = listOf(
            task(id = 1, priority = TaskPriority.LOW, dueDate = 100),
            task(id = 2, priority = TaskPriority.HIGH, dueDate = 1000),
            task(id = 3, priority = TaskPriority.HIGH, dueDate = 100),
        )
        val sorted = tasks.sortedWith(comparator).map { it.id }
        // 期望：id=3 (HIGH, due=100) > id=2 (HIGH, due=1000) > id=1 (LOW, due=100)
        assertEquals(listOf(3L, 2L, 1L), sorted)
    }

    @Test
    fun `TITLE_ASC 大小写不敏感`() {
        val comparator = TaskSort.TITLE_ASC.toComparator()
        val tasks = listOf(
            task(id = 1, title = "banana"),
            task(id = 2, title = "Apple"),
            task(id = 3, title = "cherry"),
        )
        val sorted = tasks.sortedWith(comparator).map { it.id }
        // CASE_INSENSITIVE_ORDER: A(2), b(1), c(3)
        assertEquals(listOf(2L, 1L, 3L), sorted)
    }

    @Test
    fun `所有 sort 枚举都可构造 comparator（覆盖全部分支）`() {
        // 烟雾测试：每个枚举都能 toComparator() 不抛异常
        TaskSort.entries.forEach { sort ->
            val cmp = sort.toComparator()
            val a = task(id = 1)
            val b = task(id = 2)
            val r = cmp.compare(a, b)
            assertTrue("sort=$sort produced non-Int compare: $r", r is Int)
        }
    }
}
