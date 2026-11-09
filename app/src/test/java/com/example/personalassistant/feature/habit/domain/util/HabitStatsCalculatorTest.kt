package com.example.personalassistant.feature.habit.domain.util

import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitCheckIn
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 23：习惯统计纯函数的 JVM 单测
 *
 * 覆盖：
 * - 连续天数（今天还没打卡从昨天续算 / 中间断掉要重置 / 补卡后重新连上）
 * - 每日 N 次目标的达成判定
 * - 每周 N 次目标的连续周数
 * - 月度打卡表 / 年度热力图的形状与内容
 */
class HabitStatsCalculatorTest {

    // ===== 测试夹具 =====

    /** 固定「今天」= 2024-05-15，避免用例随运行日期漂移 */
    private val today: Long = millis(2024, 5, 15)

    private fun millis(year: Int, month: Int, day: Int): Long =
        HabitDates.startOfDayMillis(LocalDate.of(year, month, day))

    private fun habit(
        targetType: HabitTargetType = HabitTargetType.DAILY,
        targetCount: Int = 1,
    ) = Habit(
        id = 1L,
        name = "喝水",
        targetType = targetType,
        targetCount = targetCount,
    )

    private fun checkIns(vararg pairs: Pair<Long, Int>): List<HabitCheckIn> =
        pairs.map { (date, count) ->
            HabitCheckIn(habitId = 1L, date = date, count = count)
        }

    // ===== 目标达成判定 =====

    @Test
    fun `每日 N 次目标 - 次数不够不算达成`() {
        assertFalse(isTargetMet(count = 2, targetCount = 3))
    }

    @Test
    fun `每日 N 次目标 - 次数够了算达成`() {
        assertTrue(isTargetMet(count = 3, targetCount = 3))
        assertTrue(isTargetMet(count = 4, targetCount = 3))
    }

    // ===== 连续天数 =====

    @Test
    fun `连续天数 - 连续三天都达成就是 3 天`() {
        val counts = mapOf(
            millis(2024, 5, 15) to 1,
            millis(2024, 5, 14) to 1,
            millis(2024, 5, 13) to 1,
        )
        assertEquals(3, computeDailyStreak(counts, today, targetCount = 1))
    }

    @Test
    fun `连续天数 - 中间断掉要重置`() {
        // 今天达成、昨天达成、前天没打卡 → 只能数到 2，前天的记录不能续上
        val counts = mapOf(
            millis(2024, 5, 15) to 1,
            millis(2024, 5, 14) to 1,
            // 5-13 缺勤
        )
        assertEquals(2, computeDailyStreak(counts, today, targetCount = 1))
    }

    @Test
    fun `连续天数 - 今天还没打卡时从昨天续算`() {
        // 今天还没打卡不算断 —— 今天还有机会达成
        val counts = mapOf(
            millis(2024, 5, 14) to 1,
            millis(2024, 5, 13) to 1,
        )
        assertEquals(2, computeDailyStreak(counts, today, targetCount = 1))
    }

    @Test
    fun `连续天数 - 今天和昨天都没打卡就是 0`() {
        assertEquals(0, computeDailyStreak(emptyMap(), today, targetCount = 1))
    }

    @Test
    fun `连续天数 - 补卡把断掉的那天补上后重新连起来`() {
        val before = mapOf(
            millis(2024, 5, 15) to 1,
            millis(2024, 5, 13) to 1,
            millis(2024, 5, 12) to 1,
        )
        // 补卡前：5-14 缺勤 → 只数到 1
        assertEquals(1, computeDailyStreak(before, today, targetCount = 1))

        // 补上 5-14 之后：5-15 → 5-14 → 5-13 → 5-12 重新连成 4 天
        val after = before + (millis(2024, 5, 14) to 1)
        assertEquals(4, computeDailyStreak(after, today, targetCount = 1))
    }

    @Test
    fun `连续天数 - 次数不够的那天不算达成`() {
        // 目标是每天 2 次，5-14 只打了 1 次 → 断在那里
        val counts = mapOf(
            millis(2024, 5, 15) to 2,
            millis(2024, 5, 14) to 1,
            millis(2024, 5, 13) to 2,
        )
        assertEquals(1, computeDailyStreak(counts, today, targetCount = 2))
    }

    // ===== 每周目标 =====

    @Test
    fun `每周 N 次目标 - 连续周数从本周往前数`() {
        val weekStart = HabitDates.startOfWeekMillis(HabitDates.toLocalDate(today))
        val weeks = mapOf(
            weekStart to 3,
            millis(2024, 5, 6) to 3,
            millis(2024, 4, 29) to 1,
        )
        assertEquals(2, computeWeeklyStreak(weeks, weekStart, targetCount = 3))
    }

    @Test
    fun `每周 N 次目标 - 本周还没达标时从上周续算`() {
        val weekStart = HabitDates.startOfWeekMillis(HabitDates.toLocalDate(today))
        val weeks = mapOf(
            // 本周没记录
            millis(2024, 5, 6) to 3,
            millis(2024, 4, 29) to 3,
        )
        assertEquals(2, computeWeeklyStreak(weeks, weekStart, targetCount = 3))
    }

    // ===== 月度打卡表 =====

    @Test
    fun `月度打卡表 - 当月每一天都有一格 没打卡的日子是 0`() {
        val cells = buildMonthGrid(
            year = 2024,
            month = 5,
            dayCounts = mapOf(millis(2024, 5, 15) to 1),
            targetCount = 1,
        )
        assertEquals(31, cells.size)
        val fifteenth = cells.first { it.date == millis(2024, 5, 15) }
        assertEquals(1, fifteenth.count)
        assertTrue(fifteenth.isMet)
        val first = cells.first { it.date == millis(2024, 5, 1) }
        assertEquals(0, first.count)
        assertFalse(first.isMet)
    }

    @Test
    fun `月度打卡表 - 二月按当年实际天数算`() {
        val cells = buildMonthGrid(year = 2024, month = 2, dayCounts = emptyMap(), targetCount = 1)
        assertEquals(29, cells.size) // 2024 是闰年
    }

    // ===== 年度热力图 =====

    @Test
    fun `年度热力图 - 按周切分 每周固定 7 格`() {
        val weeks = buildYearHeatmap(year = 2024, dayCounts = emptyMap(), targetCount = 1)
        assertTrue(weeks.size in 52..54)
        assertTrue(weeks.all { it.cells.size == 7 })
    }

    @Test
    fun `热力图色阶 - 没打卡是 0 达成目标是 4`() {
        assertEquals(0, heatmapLevel(count = 0, targetCount = 1, met = false))
        assertEquals(4, heatmapLevel(count = 1, targetCount = 1, met = true))
        val partial = heatmapLevel(count = 1, targetCount = 4, met = false)
        assertTrue(partial in 1..3)
    }

    // ===== 组装 =====

    @Test
    fun `统计组装 - 连续天数 今日次数与达标状态都对得上`() {
        val stats = buildHabitStats(
            habit = habit(targetCount = 2),
            checkIns = checkIns(
                millis(2024, 5, 15) to 2,
                millis(2024, 5, 14) to 2,
            ),
            year = 2024,
            month = 5,
            today = today,
        )
        assertEquals(2, stats.streak)
        assertEquals(2, stats.todayCount)
        assertTrue(stats.todayMet)
        assertEquals(31, stats.monthCells.size)
        assertTrue(stats.heatmapWeeks.isNotEmpty())
    }
}
