package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 8b'：[TaskDisplayPreferences] 模型层单元测试
 *
 * 测试范围：默认值构造、alias 替换、viewMode/selectedQuadrant 语义一致性。
 */
class TaskDisplayPreferencesTest {

    @Test
    fun `默认 aliases 等于 enum label 映射`() {
        val prefs = TaskDisplayPreferences()
        // 5 个象限都必须有 alias
        TaskQuadrant.entries.forEach { q ->
            assertEquals(
                "象限 ${q.name} 默认 alias 应等于 enum.label",
                q.label,
                prefs.aliases[q],
            )
        }
        assertEquals(TaskQuadrant.entries.size, prefs.aliases.size)
    }

    @Test
    fun `默认 viewMode 是 ALL 且 selectedQuadrant 为 null`() {
        val prefs = TaskDisplayPreferences()
        assertEquals(TaskViewMode.ALL, prefs.viewMode)
        assertNull(prefs.selectedQuadrant)
    }

    @Test
    fun `替换某象限 alias 不影响其他象限`() {
        val original = TaskDisplayPreferences()
        val updated = original.copy(
            aliases = original.aliases.toMutableMap().apply {
                put(TaskQuadrant.P1, "🔥 立刻做")
            },
        )

        // P1 已更新
        assertEquals("🔥 立刻做", updated.aliases[TaskQuadrant.P1])
        // 其他象限未受影响
        TaskQuadrant.entries
            .filter { it != TaskQuadrant.P1 }
            .forEach { other ->
                assertEquals(
                    "象限 ${other.name} 不应被 P1 的 alias 修改影响",
                    original.aliases[other],
                    updated.aliases[other],
                )
            }
    }

    @Test
    fun `copy 不会 mutate 默认 aliases`() {
        val first = TaskDisplayPreferences()
        val second = first.copy(
            aliases = first.aliases + (TaskQuadrant.P2 to "黄区"),
        )
        // 原对象 aliases 不被影响
        assertEquals(TaskQuadrant.P2.label, first.aliases[TaskQuadrant.P2])
        // 新对象有更新
        assertEquals("黄区", second.aliases[TaskQuadrant.P2])
        // 默认值字段仍可访问
        assertEquals(TaskQuadrant.P2.label, TaskDisplayPreferences.DEFAULT_ALIASES[TaskQuadrant.P2])
    }

    @Test
    fun `BY_QUADRANT 模式下 selectedQuadrant 必须非 null 才语义自洽`() {
        val prefs = TaskDisplayPreferences(
            viewMode = TaskViewMode.BY_QUADRANT,
            selectedQuadrant = TaskQuadrant.P3,
        )
        assertEquals(TaskViewMode.BY_QUADRANT, prefs.viewMode)
        assertEquals(TaskQuadrant.P3, prefs.selectedQuadrant)
    }

    @Test
    fun `aliasOf 在缺失 alias 时回退到 enum label`() {
        val prefs = TaskDisplayPreferences(aliases = emptyMap())
        TaskQuadrant.entries.forEach { q ->
            assertEquals(
                "aliasOf 在 aliases 为空时回退到 ${q.name}.label",
                q.label,
                TaskDisplayPreferences.aliasOf(prefs, q),
            )
        }
    }

    @Test
    fun `aliasOf 在存在 alias 时返回 alias`() {
        val prefs = TaskDisplayPreferences(
            aliases = mapOf(TaskQuadrant.UNSCHEDULED to "待定"),
        )
        assertEquals(
            "待定",
            TaskDisplayPreferences.aliasOf(prefs, TaskQuadrant.UNSCHEDULED),
        )
        // 未配置的象限回退
        assertEquals(
            TaskQuadrant.P1.label,
            TaskDisplayPreferences.aliasOf(prefs, TaskQuadrant.P1),
        )
    }
}