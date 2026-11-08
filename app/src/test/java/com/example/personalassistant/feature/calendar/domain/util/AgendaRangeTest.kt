package com.example.personalassistant.feature.calendar.domain.util

import com.example.personalassistant.feature.calendar.domain.model.AgendaScope
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日程流的聚合范围。
 *
 * 关键约束：**「日日程」只有一天**，不能偷偷展开成整周。
 * 整周是「工作日 / 休息日日程」才做的事。
 */
class AgendaRangeTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun day(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `日日程只有选中的那一天`() {
        val dates = agendaDates(day(2026, 11, 4), AgendaScope.DAY, zone)
        assertEquals(listOf(day(2026, 11, 4)), dates)
    }

    @Test
    fun `工作日日程是所在那一周的周一到周五`() {
        // 2026-11-04 是周三
        val dates = agendaDates(day(2026, 11, 4), AgendaScope.WORKDAY, zone)
        assertEquals(
            listOf(
                day(2026, 11, 2),
                day(2026, 11, 3),
                day(2026, 11, 4),
                day(2026, 11, 5),
                day(2026, 11, 6),
            ),
            dates,
        )
    }

    @Test
    fun `休息日日程是所在那一周的周六日`() {
        val dates = agendaDates(day(2026, 11, 4), AgendaScope.RESTDAY, zone)
        assertEquals(listOf(day(2026, 11, 7), day(2026, 11, 8)), dates)
    }

    @Test
    fun `锚在周日也算同一周`() {
        // 2026-11-08 是周日，那一周还是 11-02 ~ 11-08
        val dates = agendaDates(day(2026, 11, 8), AgendaScope.RESTDAY, zone)
        assertEquals(listOf(day(2026, 11, 7), day(2026, 11, 8)), dates)
    }

    @Test
    fun `锚在周一时工作日就是从这天起五天`() {
        val dates = agendaDates(day(2026, 11, 2), AgendaScope.WORKDAY, zone)
        assertEquals(5, dates.size)
        assertEquals(day(2026, 11, 2), dates.first())
        assertEquals(day(2026, 11, 6), dates.last())
    }

    @Test
    fun `工作日加休息日刚好覆盖一周`() {
        val workday = agendaDates(day(2026, 11, 4), AgendaScope.WORKDAY, zone)
        val restday = agendaDates(day(2026, 11, 4), AgendaScope.RESTDAY, zone)
        assertEquals(7, workday.size + restday.size)
        assertEquals(
            (workday + restday).sorted(),
            (workday + restday),
        )
    }

    @Test
    fun `整周是周一到周日七天连着排`() {
        val dates = agendaDates(day(2026, 11, 4), AgendaScope.WEEK, zone)
        assertEquals(7, dates.size)
        assertEquals(day(2026, 11, 2), dates.first())
        assertEquals(day(2026, 11, 8), dates.last())
        // 严格递增 —— 周视图的清单靠这个顺序排
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun `整周等于工作日加休息日`() {
        val week = agendaDates(day(2026, 11, 4), AgendaScope.WEEK, zone)
        val workday = agendaDates(day(2026, 11, 4), AgendaScope.WORKDAY, zone)
        val restday = agendaDates(day(2026, 11, 4), AgendaScope.RESTDAY, zone)
        assertEquals(week, workday + restday)
    }
}
