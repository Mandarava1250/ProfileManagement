package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7c：ObserveDayDetailUseCase 单测
 *
 * - 不依赖 Room（用 FakeCalendarRepository）
 * - Fake 的 observeDayDetail 是占位实现：返回 events + 空 tasks/courses + dayOfWeek=1
 * - 验证 UseCase 把 Repository 的 Flow 原样透传，且 dayOfWeek / date / events 字段被正确设置
 */
class ObserveDayDetailUseCaseTest {

    @Test
    fun `invoke returns DayDetail with given date and empty default fields`() = runTest {
        val repo = FakeCalendarRepository()
        val useCase = ObserveDayDetailUseCase(repo)

        val date = 1_700_000_000_000L
        val result = useCase(date).first()

        assertEquals(date, result.date)
        // Fake 简化：dayOfWeek 硬编码 1
        assertEquals(1, result.dayOfWeek)
        // 没有事件时 events / tasks / courses 都为空
        assertTrue(result.events.isEmpty())
        assertTrue(result.tasks.isEmpty())
        assertTrue(result.courses.isEmpty())
        assertEquals(null, result.weekNumber)
        assertEquals(false, result.hasAny)
    }

    @Test
    fun `invoke includes events for the given date only`() = runTest {
        val repo = FakeCalendarRepository()
        val targetDate = 1_700_000_000_000L
        // 当天 2 条
        repo.seedEvent(title = "当天-1", date = targetDate, id = 1L)
        repo.seedEvent(title = "当天-2", date = targetDate, id = 2L)
        // 其他日期
        repo.seedEvent(title = "其他", date = targetDate - 1, id = 3L)
        repo.seedEvent(title = "其他", date = targetDate + 1, id = 4L)

        val useCase = ObserveDayDetailUseCase(repo)
        val result = useCase(targetDate).first()

        assertEquals(2, result.events.size)
        assertEquals(1L, result.events[0].id)
        assertEquals(2L, result.events[1].id)
        assertEquals(true, result.hasAny)
    }

    @Test
    fun `invoke with different dates returns independent DayDetails`() = runTest {
        val repo = FakeCalendarRepository()
        val day1 = 1_700_000_000_000L
        val day2 = 1_700_086_400_000L  // +1 天
        repo.seedEvent(title = "day1 event", date = day1, id = 1L)
        repo.seedEvent(title = "day2 event", date = day2, id = 2L)

        val useCase = ObserveDayDetailUseCase(repo)
        val resultDay1 = useCase(day1).first()
        val resultDay2 = useCase(day2).first()

        assertEquals(1, resultDay1.events.size)
        assertEquals("day1 event", resultDay1.events[0].title)
        assertEquals(1, resultDay2.events.size)
        assertEquals("day2 event", resultDay2.events[0].title)
    }

    @Test
    fun `invoke reflects newly added event`() = runTest {
        val repo = FakeCalendarRepository()
        val useCase = ObserveDayDetailUseCase(repo)
        val date = 1_700_000_000_000L

        // 初始空
        assertTrue(useCase(date).first().events.isEmpty())

        // 加事件
        repo.seedEvent(title = "新加", date = date, id = 1L)

        // 重新 collect
        val updated = useCase(date).first()
        assertEquals(1, updated.events.size)
        assertEquals("新加", updated.events[0].title)
    }
}