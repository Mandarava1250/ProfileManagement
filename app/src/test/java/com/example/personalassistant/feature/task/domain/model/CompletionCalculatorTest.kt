package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 22.6：完成率统计
 */
class CompletionCalculatorTest {

    private val today = LocalDate.of(2026, 3, 15)

    @Test
    fun `空数据完成率是 0`() {
        val stats = CompletionCalculator.stats(emptyList(), today)

        assertEquals(0, stats.totalPlanned)
        assertEquals(0f, stats.completionRate, 0.0001f)
        assertTrue(stats.isEmpty)
    }

    @Test
    fun `完成率等于完成数除以计划数`() {
        val stats = CompletionCalculator.stats(
            listOf(
                DailyCompletion(today, planned = 4, completed = 1),
                DailyCompletion(today.minusDays(1), planned = 4, completed = 3),
            ),
            today,
        )

        assertEquals(8, stats.totalPlanned)
        assertEquals(4, stats.completed)
        assertEquals(0.5f, stats.completionRate, 0.0001f)
        assertEquals(50, stats.completionPercent)
    }

    @Test
    fun `计划数为 0 时不除零`() {
        val stats = CompletionCalculator.stats(
            listOf(DailyCompletion(today, planned = 0, completed = 0)),
            today,
        )

        assertEquals(0f, stats.completionRate, 0.0001f)
        assertFalse(stats.isEmpty.not())
    }

    @Test
    fun `逾期的未完成计入拖延`() {
        val stats = CompletionCalculator.stats(
            listOf(
                DailyCompletion(today.minusDays(3), planned = 2, completed = 0),
                DailyCompletion(today.minusDays(2), planned = 3, completed = 1),
                DailyCompletion(today, planned = 1, completed = 1),
            ),
            today,
        )

        // 前两天过期：2 + (3-1)=2 → 共 4
        assertEquals(4, stats.overdue)
    }

    @Test
    fun `今天未完成时连续天数从昨天数`() {
        val stats = CompletionCalculator.stats(
            listOf(
                DailyCompletion(today, planned = 1, completed = 0),
                DailyCompletion(today.minusDays(1), planned = 1, completed = 1),
                DailyCompletion(today.minusDays(2), planned = 1, completed = 1),
            ),
            today,
        )

        assertEquals("今天还没做不该把昨天的连续段清零", 2, stats.streakDays)
    }

    @Test
    fun `今天完成了就连续算上今天`() {
        val stats = CompletionCalculator.stats(
            listOf(
                DailyCompletion(today, planned = 1, completed = 1),
                DailyCompletion(today.minusDays(1), planned = 1, completed = 1),
            ),
            today,
        )

        assertEquals(2, stats.streakDays)
    }

    @Test
    fun `缺口一天连续段结束`() {
        val stats = CompletionCalculator.stats(
            listOf(
                DailyCompletion(today, planned = 1, completed = 1),
                DailyCompletion(today.minusDays(1), planned = 1, completed = 0),
                DailyCompletion(today.minusDays(2), planned = 1, completed = 1),
            ),
            today,
        )

        assertEquals(1, stats.streakDays)
    }

    @Test
    fun `连续三天`() {
        val stats = CompletionCalculator.stats(
            listOf(
                DailyCompletion(today, planned = 1, completed = 2),
                DailyCompletion(today.minusDays(1), planned = 1, completed = 1),
                DailyCompletion(today.minusDays(2), planned = 1, completed = 1),
                DailyCompletion(today.minusDays(3), planned = 1, completed = 0),
            ),
            today,
        )

        assertEquals(3, stats.streakDays)
    }

    @Test
    fun `没有数据的日期会中断连续段`() {
        val stats = CompletionCalculator.stats(
            listOf(
                DailyCompletion(today, planned = 1, completed = 1),
                // 昨天没有数据 → 连续段到此为止
                DailyCompletion(today.minusDays(2), planned = 1, completed = 1),
            ),
            today,
        )

        assertEquals(1, stats.streakDays)
    }

    @Test
    fun `零计划时不算拖延`() {
        val stats = CompletionCalculator.stats(
            listOf(DailyCompletion(today.minusDays(1), planned = 0, completed = 0)),
            today,
        )

        assertEquals(0, stats.overdue)
    }
}
