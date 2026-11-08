package com.example.personalassistant.feature.timetable.domain.util

import com.example.personalassistant.feature.timetable.domain.model.Semester
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 学期周数的边界。
 *
 * 这两个函数护着「周数不能是负数」这条线 —— 负周号会让课程全都不显示，
 * 界面却看着正常，排查起来极痛苦。
 */
class SemesterWeeksTest {

    private fun day(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun semester(start: Long, end: Long) = Semester(
        name = "2026 秋季",
        startDate = start,
        endDate = end,
    )

    @Test
    fun `总周数等于跨越天数除以七加一`() {
        // 2026-09-01 ~ 2026-12-31 共 121 天 → 121 / 7 + 1 = 18 周
        val s = semester(day(2026, 9, 1), day(2026, 12, 31))
        assertEquals(18, semesterTotalWeeks(s))
    }

    @Test
    fun `同一天开始结束算一周`() {
        val s = semester(day(2026, 9, 1), day(2026, 9, 1))
        assertEquals(1, semesterTotalWeeks(s))
    }

    @Test
    fun `不足一周也算一周`() {
        // 09-01 ~ 09-03 共 2 天 → 2 / 7 + 1 = 1 周
        val s = semester(day(2026, 9, 1), day(2026, 9, 3))
        assertEquals(1, semesterTotalWeeks(s))
    }

    @Test
    fun `负周号被钳到零`() {
        assertEquals(0, clampWeekNumber(-1, 18))
        assertEquals(0, clampWeekNumber(-999, 18))
    }

    @Test
    fun `超出学期总周数被钳到上界`() {
        assertEquals(18, clampWeekNumber(19, 18))
        assertEquals(18, clampWeekNumber(999, 18))
    }

    @Test
    fun `区间内的周号原样返回`() {
        assertEquals(0, clampWeekNumber(0, 18))
        assertEquals(9, clampWeekNumber(9, 18))
        assertEquals(18, clampWeekNumber(18, 18))
    }

    @Test
    fun `没有学期时一律钳到零`() {
        assertEquals(0, clampWeekNumber(5, 0))
        assertEquals(0, clampWeekNumber(-5, 0))
    }
}
