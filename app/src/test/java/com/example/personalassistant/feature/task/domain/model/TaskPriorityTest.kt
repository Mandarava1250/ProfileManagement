package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 Sub-step 2：TaskPriority 5 档扩展验证。
 *
 * 关注点：
 * - 5 档枚举的 dbValue 严格 0..4
 * - 排序权重 rank 严格 URGENT > HIGH > MEDIUM > NORMAL > LOW
 * - 向后兼容：旧数据 dbValue 0/1/2 仍映射到 LOW/MEDIUM/HIGH
 * - label 文案非空且互不相同
 *
 * 注：TaskTest 里已有 `dbValue roundtrips through fromDb`（entries.forEach），
 * 这里聚焦「5 档结构语义」本身。
 */
class TaskPriorityTest {

    @Test
    fun `5 档枚举 size = 5 且 dbValue 严格 0 到 4`() {
        assertEquals(5, TaskPriority.entries.size)
        val dbValues = TaskPriority.entries.map { it.dbValue }.toSet()
        assertEquals(setOf(0, 1, 2, 3, 4), dbValues)
    }

    @Test
    fun `rank 权重顺序 URGENT_HIGH_MEDIUM_NORMAL_LOW`() {
        assertTrue(TaskPriority.URGENT.rank > TaskPriority.HIGH.rank)
        assertTrue(TaskPriority.HIGH.rank > TaskPriority.MEDIUM.rank)
        assertTrue(TaskPriority.MEDIUM.rank > TaskPriority.NORMAL.rank)
        assertTrue(TaskPriority.NORMAL.rank > TaskPriority.LOW.rank)

        // 5 档 rank 互不相同（防止重复声明）
        val ranks = TaskPriority.entries.map { it.rank }.toSet()
        assertEquals(5, ranks.size)
    }

    @Test
    fun `rank 权重数值与设计一致：1_3_5_7_9`() {
        assertEquals(1, TaskPriority.LOW.rank)
        assertEquals(3, TaskPriority.NORMAL.rank)
        assertEquals(5, TaskPriority.MEDIUM.rank)
        assertEquals(7, TaskPriority.HIGH.rank)
        assertEquals(9, TaskPriority.URGENT.rank)
    }

    @Test
    fun `label 文案非空且五档互不相同`() {
        val labels = TaskPriority.entries.map { it.label }
        assertEquals(5, labels.size)
        labels.forEach { assertTrue("label 应非空: '$it'", it.isNotBlank()) }
        assertEquals(5, labels.toSet().size)
    }

    @Test
    fun `向后兼容：旧 dbValue 0_1_2 仍映射 LOW_MEDIUM_HIGH 不变`() {
        // 这是 Phase 6 之前所有数据的 ord；扩展 5 档后不能改语义
        assertEquals(TaskPriority.LOW, TaskPriority.fromDb(0))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromDb(1))
        assertEquals(TaskPriority.HIGH, TaskPriority.fromDb(2))

        // 新档位
        assertEquals(TaskPriority.NORMAL, TaskPriority.fromDb(3))
        assertEquals(TaskPriority.URGENT, TaskPriority.fromDb(4))

        // 越界 / 负值 → 默认 MEDIUM（保证旧脏数据不崩）
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromDb(-1))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromDb(99))
        // 防止 dbValue 与 rank 一一对应错位的隐式假设
        assertNotEquals(TaskPriority.MEDIUM.dbValue, TaskPriority.NORMAL.rank)
    }
}
