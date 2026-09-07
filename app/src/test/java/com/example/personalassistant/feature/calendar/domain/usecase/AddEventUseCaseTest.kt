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
 * Phase 7c Sub-step 5：AddEventUseCase 单元测试
 *
 * 覆盖：
 * - 正常新增：返回 id > 0，repository 收到事件
 * - id != 0（视为已存在）：抛 IllegalArgumentException
 * - title 为空：抛 IllegalArgumentException
 * - endTime < startTime：抛 IllegalArgumentException
 * - hasTime=false / endTime=null / startTime=null：放行（仅在 hasTime=true 时校验时间范围）
 */
class AddEventUseCaseTest {

    private lateinit var repo: FakeCalendarRepository
    private lateinit var useCase: AddEventUseCase

    @Before
    fun setup() {
        repo = FakeCalendarRepository()
        useCase = AddEventUseCase(repo)
    }

    @Test
    fun `正常新增返回 id 并写入仓库`() = runTest {
        val event = Event(title = "晨会", date = 1_700_000_000_000L, isAllDay = true)
        val id = useCase(event)
        assertTrue("id should be > 0", id > 0L)
        val saved = repo.getEventById(id)
        assertNotNull(saved)
        assertEquals("晨会", saved!!.title)
        assertEquals(1, repo.addCount)
    }

    @Test
    fun `id 不是 0 抛 IllegalArgumentException`() = runTest {
        val event = Event(id = 5L, title = "冲突", date = 1_700_000_000_000L)
        try {
            useCase(event)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("id 必须为 0"))
        }
    }

    @Test
    fun `title 为空抛 IllegalArgumentException`() = runTest {
        // 注意：空标题的 Event 在构造阶段就被 Event.init 的 require 拦下，
        // 走不到 AddEventUseCase 里那行 require，所以构造必须放在 try 内部。
        try {
            useCase(Event(title = "  ", date = 1_700_000_000_000L))
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("标题不能为空"))
        }
    }

    @Test
    fun `endTime 早于 startTime 抛 IllegalArgumentException`() = runTest {
        val event = Event(
            title = "倒叙会议",
            date = 1_700_000_000_000L,
            startTime = 1_700_000_000_000L,
            endTime = 1_699_000_000_000L,
        )
        try {
            useCase(event)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("结束时间不能早于开始时间"))
        }
    }

    @Test
    fun `startTime 为 null 时不校验时间范围`() = runTest {
        val event = Event(
            title = "无时段",
            date = 1_700_000_000_000L,
            startTime = null,
            endTime = null,
        )
        val id = useCase(event)
        assertTrue(id > 0L)
    }

    @Test
    fun `hasTime=true 且 endTime == startTime 允许`() = runTest {
        // 同一时刻 = 零时长事件（不抛）
        val ts = 1_700_000_000_000L
        val event = Event(
            title = "瞬时提醒",
            date = ts,
            startTime = ts,
            endTime = ts,
        )
        val id = useCase(event)
        assertTrue(id > 0L)
    }
}
