package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.data.local.DailyCompletionRow
import com.example.personalassistant.feature.task.data.repository.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 22.6：完成率统计 UseCase
 */
class ObserveCompletionStatsUseCaseTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 3, 15)

    /** 造一行「某天的计划 / 完成」 */
    private fun row(date: LocalDate, planned: Int, completed: Int): DailyCompletionRow =
        DailyCompletionRow(
            dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            planned = planned,
            completed = completed,
        )

    @Test
    fun `没有数据时完成率是 0`() = runTest {
        val repo = FakeTaskRepository()
        val useCase = ObserveCompletionStatsUseCase(repo)

        val stats = useCase(today = today, zoneId = zone)

        assertEquals(0, stats.totalPlanned)
        assertEquals(0f, stats.completionRate, 0.0001f)
        assertEquals(0, stats.streakDays)
    }

    @Test
    fun `窗口内的完成率按计划数算`() = runTest {
        val repo = FakeTaskRepository()
        repo.dailyCompletion = listOf(
            row(today, planned = 4, completed = 1),
            row(today.minusDays(1), planned = 4, completed = 3),
        )
        val useCase = ObserveCompletionStatsUseCase(repo)

        val stats = useCase(days = 7, today = today, zoneId = zone)

        assertEquals(8, stats.totalPlanned)
        assertEquals(4, stats.completed)
        assertEquals(50, stats.completionPercent)
    }

    @Test
    fun `逾期未完成计入拖延`() = runTest {
        val repo = FakeTaskRepository()
        repo.dailyCompletion = listOf(
            row(today.minusDays(2), planned = 3, completed = 1),
            row(today, planned = 1, completed = 1),
        )
        val useCase = ObserveCompletionStatsUseCase(repo)

        val stats = useCase(days = 7, today = today, zoneId = zone)

        assertEquals("前天逾期的 2 个", 2, stats.overdue)
    }

    @Test
    fun `连续完成天数从窗口外也能数出来`() = runTest {
        val repo = FakeTaskRepository()
        // 连续 10 天（从 6 天前到今天）都完成了 —— 窗口只有 7 天，但连续是 10
        repo.dailyCompletionUntil = (0..9).map { offset ->
            row(today.minusDays(offset.toLong()), planned = 1, completed = 1)
        }
        val useCase = ObserveCompletionStatsUseCase(repo)

        val stats = useCase(days = 7, today = today, zoneId = zone)

        assertEquals(10, stats.streakDays)
    }
}
