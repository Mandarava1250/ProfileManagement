package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import com.example.personalassistant.feature.calendar.domain.model.Event
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 7c Sub-step 5：UpdateEventUseCase 单元测试
 *
 * 覆盖：
 * - 正常更新：仓库内容被替换，updateCount 递增
 * - id <= 0：抛 IllegalArgumentException
 * - title 为空：抛 IllegalArgumentException
 * - endTime < startTime：抛 IllegalArgumentException
 * - startTime/endTime 任一为 null：跳过时间范围校验
 * - 更新不存在的 id：幂等 no-op，不抛异常
 */
class UpdateEventUseCaseTest {

    private lateinit var repo: FakeCalendarRepository
    private lateinit var useCase: UpdateEventUseCase

    private val date = 1_700_000_000_000L

    @Before
    fun setup() {
        repo = FakeCalendarRepository()
        useCase = UpdateEventUseCase(repo)
    }

    @Test
    fun `正常更新写回仓库`() = runTest {
        val id = repo.seedEvent(title = "原标题", date = date)
        useCase(Event(id = id, title = "新标题", date = date))
        val saved = repo.getEventById(id)
        assertNotNull(saved)
        assertEquals("新标题", saved!!.title)
        assertEquals(1, repo.updateCount)
    }

    @Test
    fun `id 为 0 抛 IllegalArgumentException`() = runTest {
        try {
            useCase(Event(id = 0L, title = "草稿", date = date))
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("id 必须大于 0"))
        }
        assertEquals(0, repo.updateCount)
    }

    @Test
    fun `title 为空抛 IllegalArgumentException`() = runTest {
        try {
            useCase(Event(id = 1L, title = "   ", date = date))
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("标题不能为空"))
        }
    }

    @Test
    fun `endTime 早于 startTime 抛 IllegalArgumentException`() = runTest {
        try {
            useCase(
                Event(
                    id = 1L,
                    title = "倒叙会议",
                    date = date,
                    startTime = date,
                    endTime = date - 1000L,
                ),
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("结束时间不能早于开始时间"))
        }
    }

    @Test
    fun `endTime 为 null 时不校验时间范围`() = runTest {
        val id = repo.seedEvent(title = "旧", date = date)
        useCase(
            Event(id = id, title = "无结束时间", date = date, startTime = date, endTime = null),
        )
        assertEquals("无结束时间", repo.getEventById(id)!!.title)
    }

    @Test
    fun `更新不存在的 id 保持幂等`() = runTest {
        useCase(Event(id = 999L, title = "幽灵事件", date = date))
        assertEquals(1, repo.updateCount)
        assertEquals(null, repo.getEventById(999L))
    }
}