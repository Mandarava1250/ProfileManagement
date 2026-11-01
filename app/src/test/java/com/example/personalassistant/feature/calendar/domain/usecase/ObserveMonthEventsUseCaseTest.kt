package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7c：ObserveMonthEventsUseCase 单测
 *
 * - 不依赖 Room（用 FakeCalendarRepository）
 * - 验证 UseCase 把 Repository 的 Flow 原样透传
 */
class ObserveMonthEventsUseCaseTest {

    @Test
    fun `invoke returns empty list when repository has no events`() = runTest {
        val repo = FakeCalendarRepository()
        val useCase = ObserveMonthEventsUseCase(repo)

        val result = useCase(monthStart = 0L, monthEnd = 1L).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke returns events inside range`() = runTest {
        val repo = FakeCalendarRepository()
        // 月范围 [100, 200)
        repo.seedEvent(title = "范围内-早", date = 100L, id = 1L)
        repo.seedEvent(title = "范围内-中", date = 150L, id = 2L)
        repo.seedEvent(title = "范围内-末", date = 199L, id = 3L)
        // 月范围外
        repo.seedEvent(title = "范围前", date = 99L, id = 4L)
        repo.seedEvent(title = "范围后", date = 200L, id = 5L)
        repo.seedEvent(title = "很远", date = 9999L, id = 6L)

        val useCase = ObserveMonthEventsUseCase(repo)
        val result = useCase(monthStart = 100L, monthEnd = 200L).first()

        assertEquals(3, result.size)
        // DAO 排序为 date ASC, startTime ASC；Fake 维持插入顺序
        assertEquals(1L, result[0].id)
        assertEquals(2L, result[1].id)
        assertEquals(3L, result[2].id)
    }

    @Test
    fun `invoke is invoked twice with different ranges returns different results`() = runTest {
        val repo = FakeCalendarRepository()
        repo.seedEvent(title = "1月", date = 100L, id = 1L)
        repo.seedEvent(title = "2月", date = 200L, id = 2L)

        val useCase = ObserveMonthEventsUseCase(repo)
        val january = useCase(monthStart = 0L, monthEnd = 150L).first()
        val february = useCase(monthStart = 150L, monthEnd = 300L).first()

        assertEquals(1, january.size)
        assertEquals(1L, january[0].id)
        assertEquals(1, february.size)
        assertEquals(2L, february[0].id)
    }

    @Test
    fun `invoke reflects new events added after subscription setup`() = runTest {
        val repo = FakeCalendarRepository()
        val useCase = ObserveMonthEventsUseCase(repo)

        // 初始空
        assertTrue(useCase(0L, 1000L).first().isEmpty())

        // 写入新事件
        repo.seedEvent(title = "新加", date = 500L, id = 1L)

        // 重新 collect（first 取最新值）
        val updated = useCase(0L, 1000L).first()
        assertEquals(1, updated.size)
        assertEquals("新加", updated[0].title)
    }
}