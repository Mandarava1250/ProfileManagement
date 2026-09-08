package com.example.personalassistant.core.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 8 Sub-step 9 Step 5：DefaultLunarCalendar 单元测试
 *
 * 涟宝解读：lunar-java 是纯 Java 库，JVM 单测可以直接跑，无需 Android runtime。
 *
 * 测试覆盖：
 * 1. 春节：公历 2026-02-17 = 农历正月初一，节日「春节」
 * 2. 春节次日：公历 2026-02-18 = 「初二」（节日优先级已被前一日吃掉）
 * 3. 国庆节：公历 2026-10-01 = 公历节日「国庆节」
 * 4. 元旦：公历 2026-01-01 = 公历节日「元旦」，农历冬月十三（验证 dayInChinese 长字符串）
 * 5. 中秋节：公历 2025-10-06 = 农历节日「中秋节」
 * 6. timestamp 边界：传入当天任意时分秒，输出应一致（依赖系统时区 00:00 截断）
 * 7. displayLabel 优先级：节日 > 公历节日 > 月/日短标签
 */
class LunarCalendarTest {

    private val calendar = DefaultLunarCalendar()

    private fun timestampOf(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long {
        return LocalDate.of(year, month, day)
            .atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    // ---- 春节 ----

    @Test
    fun `春节 2026-02-17 是农历正月初一`() {
        val date = calendar.toLunarDate(timestampOf(2026, 2, 17))
        assertEquals("春节是 lunar festival，不在 solarFestivals", emptyList<String>(), date.solarFestivals)
        assertEquals(2026, date.year)
        assertEquals(1, date.month)
        assertEquals(1, date.day)
        assertEquals("正月", date.monthInChinese)
        assertEquals("初一", date.dayInChinese)
        assertTrue("春节必须在 lunar festivals", date.festivals.contains("春节"))
    }

    @Test
    fun `春节 displayLabel 输出节日名`() {
        val date = calendar.toLunarDate(timestampOf(2026, 2, 17))
        assertEquals("春节", calendar.displayLabel(date))
    }

    // ---- 春节次日：测试非节日场景 ----

    @Test
    fun `春节次日 2026-02-18 是初二`() {
        val date = calendar.toLunarDate(timestampOf(2026, 2, 18))
        assertEquals("次日不是初一", 2, date.day)
        assertEquals("初二", date.dayInChinese)
        assertTrue("次日不应当是春节节日", date.festivals.isEmpty())
        assertEquals("shortLabel 应当是初二", "初二", calendar.shortLabel(date))
        assertEquals("displayLabel 在无节日时退化为 shortLabel", "初二", calendar.displayLabel(date))
    }

    // ---- 国庆节（公历节日）----

    @Test
    fun `国庆节 2026-10-01 是公历节日`() {
        val date = calendar.toLunarDate(timestampOf(2026, 10, 1))
        assertTrue("国庆节必须在 solarFestivals", date.solarFestivals.contains("国庆节"))
        assertEquals("displayLabel 优先节日名", "国庆节", calendar.displayLabel(date))
    }

    // ---- 元旦 + 跨年长字符串 ----

    @Test
    fun `元旦 2026-01-01 既是公历节日又农历冬月十三`() {
        val date = calendar.toLunarDate(timestampOf(2026, 1, 1))
        // lunar-java 在公历节日列表里写的是「元旦节」（带「节」字）
        assertTrue("元旦节必须在 solarFestivals", date.solarFestivals.contains("元旦节"))
        // 2026-01-01 = 乙巳年冬月十三
        assertEquals("冬月", date.monthInChinese)
        assertEquals("十三", date.dayInChinese)
        // displayLabel 优先节日
        assertEquals("元旦节", calendar.displayLabel(date))
    }

    // ---- 中秋节 ----

    @Test
    fun `中秋节 2025-10-06 是农历八月十五`() {
        val date = calendar.toLunarDate(timestampOf(2025, 10, 6))
        assertEquals(2025, date.year)
        assertEquals("八月", date.monthInChinese)
        assertEquals("十五", date.dayInChinese)
        assertTrue("中秋节必须在 lunar festivals", date.festivals.contains("中秋节"))
        assertEquals("中秋节", calendar.displayLabel(date))
    }

    // ---- timestamp 边界 ----

    @Test
    fun `timestamp 当天任意时分秒输出一致`() {
        val morning = timestampOf(2026, 2, 17, 9, 30)
        val evening = timestampOf(2026, 2, 17, 23, 59)
        val midnight = timestampOf(2026, 2, 17, 0, 0)

        val dateMorning = calendar.toLunarDate(morning)
        val dateEvening = calendar.toLunarDate(evening)
        val dateMidnight = calendar.toLunarDate(midnight)

        // 都是正月初一，节日都是「春节」
        assertEquals(dateMorning.month, dateEvening.month)
        assertEquals(dateMorning.day, dateEvening.day)
        assertEquals(dateMorning.dayInChinese, dateEvening.dayInChinese)
        assertEquals(dateMorning.festivals, dateEvening.festivals)
        assertEquals(dateMorning.dayInChinese, dateMidnight.dayInChinese)
    }

    // ---- 普通日期：day != 1 ----

    @Test
    fun `普通日期使用 dayInChinese 短标签`() {
        // 2026-01-15 是乙巳年十一月廿七（day=27 → "廿七"）
        val date = calendar.toLunarDate(timestampOf(2026, 1, 15))
        assertEquals("廿七", date.dayInChinese)
        assertEquals("廿七", calendar.shortLabel(date))
        assertTrue("普通日期不应触发任何节日", date.festivals.isEmpty())
        assertTrue("普通日期不应触发公历节日", date.solarFestivals.isEmpty())
    }

    // ---- 月首展示 ----

    @Test
    fun `月首 day 等于 1 时 shortLabel 显示月份`() {
        // 2026-02-17 是正月初一 → shortLabel 应当是「正月」
        val date = calendar.toLunarDate(timestampOf(2026, 2, 17))
        // 注意：displayLabel 优先春节，但 shortLabel 应该返回月份
        assertEquals("正月", calendar.shortLabel(date))
    }
}