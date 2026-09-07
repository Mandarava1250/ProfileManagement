package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 7c Sub-step 5：DeleteEventUseCase 单元测试
 *
 * 覆盖：
 * - 正常删除：事件从仓库消失，deleteCount 递增
 * - id = 0 / 负数：抛 IllegalArgumentException，且不触达 repository
 * - 删除不存在的 id：幂等 no-op，不抛异常
 * - 只删目标事件，不影响其他事件
 */
class DeleteEventUseCaseTest {

    private lateinit var repo: FakeCalendarRepository
    private lateinit var useCase: DeleteEventUseCase

    private val date = 1_700_000_000_000L

    @Before
    fun setup() {
        repo = FakeCalendarRepository()
        useCase = DeleteEventUseCase(repo)
    }

    @Test
    fun `正常删除移除事件`() = runTest {
        val id = repo.seedEvent(title = "待删除", date = date)
        useCase(id)
        assertNull(repo.getEventById(id))
        assertEquals(1, repo.deleteCount)
    }

    @Test
    fun `id 为 0 抛 IllegalArgumentException`() = runTest {
        try {
            useCase(0L)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("id 必须大于 0"))
        }
        assertEquals(0, repo.deleteCount)
    }

    @Test
    fun `id 为负数抛 IllegalArgumentException`() = runTest {
        try {
            useCase(-3L)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("id 必须大于 0"))
        }
        assertEquals(0, repo.deleteCount)
    }

    @Test
    fun `删除不存在的 id 保持幂等`() = runTest {
        useCase(999L)
        assertEquals(1, repo.deleteCount)
    }

    @Test
    fun `只删除目标事件`() = runTest {
        val keep = repo.seedEvent(title = "保留", date = date)
        val drop = repo.seedEvent(title = "删除", date = date)
        useCase(drop)
        assertNotNull(repo.getEventById(keep))
        assertNull(repo.getEventById(drop))
    }
}