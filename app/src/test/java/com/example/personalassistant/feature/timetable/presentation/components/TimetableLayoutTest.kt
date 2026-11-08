package com.example.personalassistant.feature.timetable.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 周网格纵向布局的回归测试。
 *
 * 覆盖 Phase 26 修的那个存量 bug：`.height(h)` 后面跟 `.padding(top = t)`
 * 会把 t 从 h 里扣掉，导致排在后面的课（9:45 之后）高度被压成 0、整门课不显示。
 *
 * 这个测试守住的是**算式层面**的不变式 —— 高度只跟时长有关，跟排在第几行无关。
 * Modifier 链本身的顺序要靠 instrumented test 量真实尺寸才能验，
 * 但只要算式是对的、Modifier 用的是 offset 而不是 padding，bug 就不会回来。
 */
class TimetableLayoutTest {

    private val minuteHeightDp = 0.8f
    /** 网格从 08:00 开始 */
    private val firstStart = 8 * 60

    @Test
    fun `非首节课的高度不为零`() {
        // 09:45 - 10:30，正是以前消失的那批课
        val slot = courseSlot(
            startTime = 9 * 60 + 45,
            endTime = 10 * 60 + 30,
            firstStart = firstStart,
            minuteHeightDp = minuteHeightDp,
        )

        assertTrue("非首节课的高度必须为正，实际 ${slot.heightDp}", slot.heightDp > 0f)
        // 45 分钟 × 0.8 dp/分
        assertEquals(36f, slot.heightDp, 0.001f)
        // 距网格顶部 105 分钟 × 0.8 dp/分
        assertEquals(84f, slot.topDp, 0.001f)
    }

    @Test
    fun `高度只取决于时长与起始位置无关`() {
        // 同一门 45 分钟的课，分别放在第一节和第三节
        val first = courseSlot(8 * 60, 8 * 60 + 45, firstStart, minuteHeightDp)
        val later = courseSlot(9 * 60 + 45, 10 * 60 + 30, firstStart, minuteHeightDp)

        assertEquals(first.heightDp, later.heightDp, 0.001f)
        assertTrue(later.topDp > first.topDp)
    }

    @Test
    fun `padding 写法会把高度吃掉`() {
        // 这是 bug 的算式根源：padding(top) 之后，可用高度 = height - top。
        // 排在 09:45 的课 height=36dp、top=84dp，可用高度变成 -48dp → 课被压没。
        val slot = courseSlot(9 * 60 + 45, 10 * 60 + 30, firstStart, minuteHeightDp)
        val availableWithPadding = slot.heightDp - slot.topDp

        assertTrue(
            "padding(top) 写法下这节课的可用高度必须是负的（课会被压没），实际 $availableWithPadding",
            availableWithPadding < 0f,
        )
        // 而 offset 写法下高度不受影响
        assertTrue(slot.heightDp > 0f)
    }

    @Test
    fun `开始时间早于网格起点时偏移夹到零而不是负数`() {
        val slot = courseSlot(7 * 60 + 30, 8 * 60 + 30, firstStart, minuteHeightDp)

        assertEquals(0f, slot.topDp, 0.001f)
        assertTrue(slot.heightDp > 0f)
    }
}
