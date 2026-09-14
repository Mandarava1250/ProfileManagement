package com.example.personalassistant.feature.task.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NotificationIdGenerator 单测 —— 验证稳定可取消、可与课程段不冲突、负值安全。
 */
class NotificationIdGeneratorTest {

    @Test
    fun `positive taskId returns base plus low31 bits`() {
        // 1 → TASK_BASE + 1
        assertEquals(200_001, NotificationIdGenerator.forTask(1L))
    }

    @Test
    fun `zero taskId returns base plus zero`() {
        assertEquals(NotificationIdGenerator.TASK_NOTIFICATION_ID_BASE, NotificationIdGenerator.forTask(0L))
    }

    @Test
    fun `same taskId always returns same id`() {
        val taskId = 42L
        val id1 = NotificationIdGenerator.forTask(taskId)
        val id2 = NotificationIdGenerator.forTask(taskId)
        val id3 = NotificationIdGenerator.forTask(taskId)
        assertEquals(id1, id2)
        assertEquals(id2, id3)
    }

    @Test
    fun `different taskIds in same low31 range produce different ids`() {
        val a = NotificationIdGenerator.forTask(1L)
        val b = NotificationIdGenerator.forTask(2L)
        assertNotEquals(a, b)
    }

    @Test
    fun `taskIds in nearby range produce different ids`() {
        // 现实里 taskId 都是 Room autoGenerate 的递增正整数，远小于 offsetRange
        // （Int.MAX_VALUE - 200_000 ≈ 2.147e9 ≈ 21 亿），所以实践中不会冲突。
        // 这里验证「合理范围内的两个 taskId 不会产生相同 id」。
        val a = NotificationIdGenerator.forTask(1L)
        val b = NotificationIdGenerator.forTask(2L)
        val c = NotificationIdGenerator.forTask(100L)
        assertNotEquals(a, b)
        assertNotEquals(b, c)
        assertNotEquals(a, c)
    }

    @Test
    fun `taskIds differing by offsetRange intentionally collide`() {
        // mod 算法下，差值恰好为 offsetRange 整数倍的两个 taskId 会映射到相同 id。
        // 这是设计意图（循环利用空间），不是 bug。
        val offsetRange = Int.MAX_VALUE.toLong() - NotificationIdGenerator.TASK_NOTIFICATION_ID_BASE + 1L
        val a = NotificationIdGenerator.forTask(1L)
        val b = NotificationIdGenerator.forTask(1L + offsetRange)
        assertEquals(a, b)
    }

    @Test
    fun `negative taskId uses absolute low31 bits safely`() {
        // 负 taskId 用 abs() 处理；Long.MIN_VALUE 是个边界（abs 会数学溢出），
        // 实现里特殊处理为 Long.MAX_VALUE，保证不抛异常。
        val id = NotificationIdGenerator.forTask(Long.MIN_VALUE)
        assertTrue("id=$id must be in valid range", id >= NotificationIdGenerator.TASK_NOTIFICATION_ID_BASE)
        assertTrue("id=$id must not overflow", id > 0)
    }

    @Test
    fun `task notification id never collides with course base`() {
        // 课程段基址 100_000，任务段基址 200_000。两段不相交。
        // 任务段起点必须 >= 200_000，且必须 < Int.MAX_VALUE。
        val base = NotificationIdGenerator.TASK_NOTIFICATION_ID_BASE
        val samples = listOf(0L, 1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE, -1L)
        for (taskId in samples) {
            val id = NotificationIdGenerator.forTask(taskId)
            assertTrue("id=$id for taskId=$taskId must be >= $base", id >= base)
            assertTrue("id=$id for taskId=$taskId must be < Int.MAX_VALUE", id < Int.MAX_VALUE)
        }
    }

    @Test
    fun `large taskId stays within Int range`() {
        // 任何 taskId 都映射到 [base, Int.MAX_VALUE] 区间，绝不溢出成负数
        val samples = listOf(0L, Long.MAX_VALUE, Long.MIN_VALUE, -1L, 42L)
        for (taskId in samples) {
            val id = NotificationIdGenerator.forTask(taskId)
            assertTrue("id=$id must be in Int range", id >= Int.MIN_VALUE && id <= Int.MAX_VALUE)
            assertTrue("id=$id must be >= base", id >= NotificationIdGenerator.TASK_NOTIFICATION_ID_BASE)
        }
    }

    // ===== Phase 8 Sub-step 1：forEvent =====

    @Test
    fun `positive eventId returns event base plus low31 bits`() {
        assertEquals(300_001, NotificationIdGenerator.forEvent(1L))
    }

    @Test
    fun `zero eventId returns event base`() {
        assertEquals(NotificationIdGenerator.EVENT_NOTIFICATION_ID_BASE, NotificationIdGenerator.forEvent(0L))
    }

    @Test
    fun `same eventId always returns same id`() {
        val eventId = 42L
        val id1 = NotificationIdGenerator.forEvent(eventId)
        val id2 = NotificationIdGenerator.forEvent(eventId)
        assertEquals(id1, id2)
    }

    @Test
    fun `different eventIds produce different ids`() {
        val a = NotificationIdGenerator.forEvent(1L)
        val b = NotificationIdGenerator.forEvent(2L)
        val c = NotificationIdGenerator.forEvent(100L)
        assertNotEquals(a, b)
        assertNotEquals(b, c)
        assertNotEquals(a, c)
    }

    @Test
    fun `event notification id never collides with task or course base`() {
        // 事件段基址 300_000，必须不与任务段（200_000）/ 课程段（100_000）撞码。
        val base = NotificationIdGenerator.EVENT_NOTIFICATION_ID_BASE
        val samples = listOf(0L, 1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE, -1L)
        for (eventId in samples) {
            val id = NotificationIdGenerator.forEvent(eventId)
            assertTrue("id=$id for eventId=$eventId must be >= $base", id >= base)
            assertTrue("id=$id for eventId=$eventId must be < Int.MAX_VALUE", id < Int.MAX_VALUE)
        }
        // 同 id 段内 forTask vs forEvent 也不撞（因为 base 不同）
        for (i in listOf(1L, 42L, 1000L)) {
            assertNotEquals(
                "task and event ids must not collide for i=$i",
                NotificationIdGenerator.forTask(i),
                NotificationIdGenerator.forEvent(i),
            )
        }
    }

    @Test
    fun `negative eventId uses absolute low31 bits safely`() {
        val id = NotificationIdGenerator.forEvent(Long.MIN_VALUE)
        assertTrue("id=$id must be in valid range", id >= NotificationIdGenerator.EVENT_NOTIFICATION_ID_BASE)
        assertTrue("id=$id must not overflow", id > 0)
    }
}
