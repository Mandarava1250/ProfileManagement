package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务 Domain 模型的基础测试
 *
 * 重点覆盖：
 * - TaskPriority / RepeatType 的 DB 反序列化容错（未知值回退默认值）
 * - Task 数据类的默认值合理性
 */
class TaskTest {

    // ===== TaskPriority.fromDb =====

    @Test
    fun `TaskPriority fromDb maps known values`() {
        assertEquals(TaskPriority.LOW, TaskPriority.fromDb(0))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromDb(1))
        assertEquals(TaskPriority.HIGH, TaskPriority.fromDb(2))
    }

    @Test
    fun `TaskPriority fromDb falls back to MEDIUM for unknown`() {
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromDb(-1))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromDb(99))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromDb(Int.MAX_VALUE))
    }

    @Test
    fun `TaskPriority dbValue roundtrips through fromDb`() {
        TaskPriority.entries.forEach { p ->
            assertEquals(p, TaskPriority.fromDb(p.dbValue))
        }
    }

    // ===== RepeatType.fromDb =====

    @Test
    fun `RepeatType fromDb maps known values including null`() {
        assertEquals(RepeatType.NONE, RepeatType.fromDb(null))
        assertEquals(RepeatType.DAILY, RepeatType.fromDb("daily"))
        assertEquals(RepeatType.WEEKLY, RepeatType.fromDb("weekly"))
        assertEquals(RepeatType.MONTHLY, RepeatType.fromDb("monthly"))
        assertEquals(RepeatType.YEARLY, RepeatType.fromDb("yearly"))
    }

    @Test
    fun `RepeatType fromDb falls back to NONE for unknown string`() {
        assertEquals(RepeatType.NONE, RepeatType.fromDb("garbage"))
        assertEquals(RepeatType.NONE, RepeatType.fromDb(""))
        assertEquals(RepeatType.NONE, RepeatType.fromDb("DAILY"))  // 大小写敏感
    }

    // ===== Task 默认值 =====

    @Test
    fun `Task default values are sensible`() {
        val t = Task(title = "hello")
        assertEquals(0L, t.id)
        assertEquals("hello", t.title)
        assertNull(t.description)
        assertNull(t.dueDate)
        assertEquals(TaskPriority.MEDIUM, t.priority)
        assertFalse(t.isCompleted)
        assertNull(t.category)
        assertNull(t.reminderTime)
        assertEquals(RepeatType.NONE, t.repeatType)
        assertNull(t.repeatEndDate)
        assertTrue(t.createdAt > 0L)
        assertTrue(t.updatedAt > 0L)
    }
}
