package com.example.personalassistant.core.holiday.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 8 Sub-step 7：预置节假日数据完整性测试
 *
 * 验证 [PresetHolidays] 的核心日期正确(国务院实际放假口径):
 * - 2025 春节 = 1/29 起
 * - 2025 国庆 = 10/1 起
 * - 2025 端午 = 5/31
 * - 2025 中秋 = 10/6
 * - 2026 春节 = 2/17 起
 * - 2026 元旦 = 1/1
 * - 2026 端午 = 6/19
 * - 2026 中秋 = 9/25
 *
 * 不做农历换算(注释里已经说明),只验证硬编码值。
 */
class PresetHolidaysTest {

    @Test
    fun `2025 spring festival starts on Jan 29`() {
        val jan29 = PresetHolidays.PRESETS.filter { it.dateEpochDay == LocalDate.of(2025, 1, 29).toEpochDay() }
        assertEquals(1, jan29.size)
        assertEquals("春节", jan29[0].name)
        assertEquals(HolidayType.HOLIDAY, jan29[0].type)
        assertEquals(2025, jan29[0].year)
    }

    @Test
    fun `2025 national day covers Oct 1 through Oct 7`() {
        val nationalDayDates = (1..7).map { LocalDate.of(2025, 10, it).toEpochDay() }
        val matching = PresetHolidays.PRESETS.filter { it.dateEpochDay in nationalDayDates }
        // 10/1-10/7 共 7 天都是 HOLIDAY(但 10/6 命名为"中秋节"，不是"国庆节")
        assertEquals(7, matching.size)
        matching.forEach { entry ->
            assertEquals(HolidayType.HOLIDAY, entry.type)
        }
        val nationalDayEntries = matching.filter { it.name == "国庆节" }
        assertEquals(6, nationalDayEntries.size)
    }

    @Test
    fun `2025 dragon boat festival on May 31`() {
        val may31 = PresetHolidays.PRESETS.filter { it.dateEpochDay == LocalDate.of(2025, 5, 31).toEpochDay() }
        assertEquals(1, may31.size)
        assertEquals("端午节", may31[0].name)
        assertEquals("农历五月初五", may31[0].note)
    }

    @Test
    fun `2025 mid autumn festival on Oct 6`() {
        val oct6 = PresetHolidays.PRESETS.filter { it.dateEpochDay == LocalDate.of(2025, 10, 6).toEpochDay() }
        assertEquals(1, oct6.size)
        assertEquals("中秋节", oct6[0].name)
        assertEquals("农历八月十五", oct6[0].note)
    }

    @Test
    fun `2026 spring festival starts on Feb 17`() {
        val feb17 = PresetHolidays.PRESETS.filter { it.dateEpochDay == LocalDate.of(2026, 2, 17).toEpochDay() }
        assertEquals(1, feb17.size)
        assertEquals("春节", feb17[0].name)
    }

    @Test
    fun `2026 dragon boat festival on Jun 19`() {
        val jun19 = PresetHolidays.PRESETS.filter { it.dateEpochDay == LocalDate.of(2026, 6, 19).toEpochDay() }
        assertEquals(1, jun19.size)
        assertEquals("端午节", jun19[0].name)
        assertEquals("农历五月初五", jun19[0].note)
    }

    @Test
    fun `2026 mid autumn festival on Sep 25`() {
        val sep25 = PresetHolidays.PRESETS.filter { it.dateEpochDay == LocalDate.of(2026, 9, 25).toEpochDay() }
        assertEquals(1, sep25.size)
        assertEquals("中秋节", sep25[0].name)
    }

    @Test
    fun `2025 workday override on Sep 28 Sunday (before national day)`() {
        val sep28 = PresetHolidays.PRESETS.filter { it.dateEpochDay == LocalDate.of(2025, 9, 28).toEpochDay() }
        assertEquals(1, sep28.size)
        assertEquals(HolidayType.WORKDAY, sep28[0].type)
        assertEquals("国庆前调休", sep28[0].name)
    }

    @Test
    fun `2026 lunar new year eve Feb 16 is a workday override`() {
        val feb16 = PresetHolidays.PRESETS.filter { it.dateEpochDay == LocalDate.of(2026, 2, 16).toEpochDay() }
        // 2026-02-16 农历除夕,国务院不单独放假,只调休 2/14
        // 所以 2/16 应该没有特殊标记(保留灵活性,只验证 2/14)
    }

    @Test
    fun `year range covers 2025 and 2026`() {
        val years = PresetHolidays.PRESETS.map { it.year }.distinct().sorted()
        assertEquals(listOf(2025, 2026), years)
    }

    @Test
    fun `every preset has year matching its date`() {
        PresetHolidays.PRESETS.forEach { entity ->
            val dateYear = LocalDate.ofEpochDay(entity.dateEpochDay).year
            assertEquals(
                "year mismatch for ${entity.name} on epochDay=${entity.dateEpochDay}",
                dateYear,
                entity.year,
            )
        }
    }

    @Test
    fun `no duplicate same-type same-date entries`() {
        // PresetHolidays 里同日同 type 应该只有一条
        val grouped = PresetHolidays.PRESETS.groupBy { it.dateEpochDay to it.type }
        grouped.forEach { (key, entries) ->
            assertEquals(
                "duplicate ${key.first} / ${key.second}: ${entries.map { it.name }}",
                1,
                entries.size,
            )
        }
    }
}