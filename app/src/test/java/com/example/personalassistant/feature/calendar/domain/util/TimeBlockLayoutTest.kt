package com.example.personalassistant.feature.calendar.domain.util

import com.example.personalassistant.feature.calendar.domain.util.TimeBlockLayout.ColumnPlacement
import com.example.personalassistant.feature.calendar.domain.util.TimeBlockLayout.RowSpan
import com.example.personalassistant.feature.calendar.domain.util.TimeBlockLayout.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 时间轴布局纯计算的边界测试 —— Phase 14
 *
 * 这些场景跑 App 时很难肉眼分辨（差半小时 / 差一行），
 * 所以在这里用数字钉死。
 */
class TimeBlockLayoutTest {

    // ===== toRowSpan =====

    @Test
    fun `整点区间占一行`() {
        val span = TimeBlockLayout.toRowSpan(startMinute = 8 * 60, endMinute = 9 * 60)
        assertEquals(RowSpan(rowOffset = 8, rowSpan = 1), span)
    }

    @Test
    fun `超过一小时的区间向上取整为两行`() {
        // 08:00 - 09:40
        val span = TimeBlockLayout.toRowSpan(startMinute = 480, endMinute = 580)
        assertEquals(RowSpan(rowOffset = 8, rowSpan = 2), span)
    }

    @Test
    fun `起点向下取整到整小时`() {
        // 08:30 - 09:00 落在第 8 行（08:00 那一行）
        val span = TimeBlockLayout.toRowSpan(startMinute = 510, endMinute = 540)
        assertEquals(RowSpan(rowOffset = 8, rowSpan = 1), span)
    }

    @Test
    fun `零时长也占一行否则屏幕上看不见`() {
        val span = TimeBlockLayout.toRowSpan(startMinute = 480, endMinute = 480)
        assertEquals(RowSpan(rowOffset = 8, rowSpan = 1), span)
    }

    @Test
    fun `零点落在第一行`() {
        val span = TimeBlockLayout.toRowSpan(startMinute = 0, endMinute = 60)
        assertEquals(RowSpan(rowOffset = 0, rowSpan = 1), span)
    }

    @Test
    fun `当天最后一刻仍在二十四行之内`() {
        // 23:30 - 24:00
        val span = TimeBlockLayout.toRowSpan(startMinute = 1410, endMinute = 1440)
        assertEquals(RowSpan(rowOffset = 23, rowSpan = 1), span)
    }

    @Test
    fun `结束早于开始的脏数据按零时长处理`() {
        val span = TimeBlockLayout.toRowSpan(startMinute = 600, endMinute = 500)
        assertEquals(RowSpan(rowOffset = 10, rowSpan = 1), span)
    }

    @Test
    fun `负数开始时间被夹到零点`() {
        val span = TimeBlockLayout.toRowSpan(startMinute = -30, endMinute = 30)
        assertEquals(RowSpan(rowOffset = 0, rowSpan = 1), span)
    }

    @Test
    fun `超出一天的时间被夹住不会画出屏幕外`() {
        val span = TimeBlockLayout.toRowSpan(startMinute = 1400, endMinute = 2000)
        assertEquals(RowSpan(rowOffset = 23, rowSpan = 1), span)
    }

    // ===== assignColumns =====

    @Test
    fun `空列表没有分栏结果`() {
        assertEquals(emptyList<ColumnPlacement>(), TimeBlockLayout.assignColumns(emptyList()))
    }

    @Test
    fun `单个区间独占一列`() {
        val placements = TimeBlockLayout.assignColumns(listOf(TimeRange(480, 540)))
        assertEquals(listOf(ColumnPlacement(columnIndex = 0, columnCount = 1)), placements)
    }

    @Test
    fun `不重叠的区间共用一列`() {
        val placements = TimeBlockLayout.assignColumns(
            listOf(
                TimeRange(480, 540),   // 08:00-09:00
                TimeRange(600, 660),   // 10:00-11:00
            )
        )
        assertEquals(listOf(ColumnPlacement(0, 1), ColumnPlacement(0, 1)), placements)
    }

    @Test
    fun `重叠的区间分到两列`() {
        val placements = TimeBlockLayout.assignColumns(
            listOf(
                TimeRange(480, 600),   // 08:00-10:00
                TimeRange(540, 660),   // 09:00-11:00 与上一个重叠
            )
        )
        assertEquals(listOf(ColumnPlacement(0, 2), ColumnPlacement(1, 2)), placements)
    }

    @Test
    fun `三门课挤在同一小时分三列`() {
        val placements = TimeBlockLayout.assignColumns(
            listOf(
                TimeRange(480, 540),
                TimeRange(480, 540),
                TimeRange(480, 540),
            )
        )
        assertEquals(
            listOf(ColumnPlacement(0, 3), ColumnPlacement(1, 3), ColumnPlacement(2, 3)),
            placements,
        )
    }

    @Test
    fun `包含关系的两个区间也要分栏`() {
        val placements = TimeBlockLayout.assignColumns(
            listOf(
                TimeRange(480, 600),   // 08:00-10:00
                TimeRange(510, 540),   // 08:30-09:00 完全落在里面
            )
        )
        assertEquals(listOf(ColumnPlacement(0, 2), ColumnPlacement(1, 2)), placements)
    }

    @Test
    fun `分栏结果与入参顺序一致不会因为排序而错位`() {
        val ranges = listOf(
            TimeRange(600, 660),   // 10:00 那一段
            TimeRange(480, 540),   // 08:00 那一段
            TimeRange(500, 560),   // 08:20 与第二段重叠
        )
        val placements = TimeBlockLayout.assignColumns(ranges)
        // 第 0 个（10:00）不与任何人重叠 → 第 0 列
        assertEquals(ColumnPlacement(0, 2), placements[0])
        // 第 1 个（08:00）与第 2 个（08:20）重叠 → 分别是第 0 列和第 1 列
        assertEquals(ColumnPlacement(0, 2), placements[1])
        assertEquals(ColumnPlacement(1, 2), placements[2])
    }

    @Test
    fun `相邻但不重叠的区间不算冲突`() {
        // 08:00-09:00 与 09:00-10:00 首尾相接，可以共用一列
        val placements = TimeBlockLayout.assignColumns(
            listOf(
                TimeRange(480, 540),
                TimeRange(540, 600),
            )
        )
        assertEquals(listOf(ColumnPlacement(0, 1), ColumnPlacement(0, 1)), placements)
    }
}
