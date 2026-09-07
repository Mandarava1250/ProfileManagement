package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * ScheduleEventReminderUseCase 单测 —— 验证 triggerAt 计算和调度条件。
 */
class ScheduleEventReminderUseCaseTest {

    /**
     * 用于捕获最近一次 schedule() 调用的 Fake。
     * 不依赖 Mockito，纯手写便于断点到 ReminderInfo。
     */
    private class CapturingScheduler : ReminderScheduler {
        var lastInfo: ReminderInfo? = null
        var scheduleCalls: Int = 0

        override suspend fun schedule(info: ReminderInfo) {
            lastInfo = info
            scheduleCalls++
        }

        override suspend fun cancel(eventId: Long) = Unit
        override suspend fun cancelAll() = Unit
    }

    @Test
    fun `event with reminder and startTime triggers schedule with triggerAt = startTime minus reminder minutes`() = runTest {
        val scheduler = CapturingScheduler()
        val useCase = ScheduleEventReminderUseCase(scheduler)

        val startMillis = Instant.parse("2026-12-01T10:00:00Z").toEpochMilli()
        val event = Event(
            id = 7L,
            title = "周会",
            date = startMillis,
            startTime = startMillis,
            endTime = startMillis + 60 * 60_000L,
            reminder = 15,
            note = "准备好会议纪要",
        )

        useCase(event)

        assertEquals(1, scheduler.scheduleCalls)
        val info = scheduler.lastInfo!!
        assertEquals(7L, info.taskId) // type Long, here it carries eventId
        assertEquals("周会", info.title)
        assertEquals("准备好会议纪要", info.body)
        // triggerAt = startTime - 15 minutes
        val expectedTrigger = startMillis - 15L * 60_000L
        assertEquals(expectedTrigger, info.triggerAt.toEpochMilli())
    }

    @Test
    fun `event without reminder is a no-op`() = runTest {
        val scheduler = CapturingScheduler()
        val useCase = ScheduleEventReminderUseCase(scheduler)

        val startMillis = Instant.parse("2026-12-01T10:00:00Z").toEpochMilli()
        val event = Event(
            id = 7L,
            title = "无提醒",
            date = startMillis,
            startTime = startMillis,
            reminder = null,
        )

        useCase(event)

        assertEquals(0, scheduler.scheduleCalls)
        assertNull(scheduler.lastInfo)
    }

    @Test
    fun `all-day event without startTime is a no-op`() = runTest {
        val scheduler = CapturingScheduler()
        val useCase = ScheduleEventReminderUseCase(scheduler)

        val dateMillis = Instant.parse("2026-12-01T00:00:00Z").toEpochMilli()
        val event = Event(
            id = 8L,
            title = "全天事件",
            date = dateMillis,
            isAllDay = true,
            startTime = null,
            endTime = null,
            reminder = 30, // 即便用户填了 reminder 也无触发时刻
        )

        useCase(event)

        assertEquals(0, scheduler.scheduleCalls)
    }

    @Test
    fun `reminder zero means fire at startTime itself`() = runTest {
        val scheduler = CapturingScheduler()
        val useCase = ScheduleEventReminderUseCase(scheduler)

        val startMillis = Instant.parse("2026-12-01T10:00:00Z").toEpochMilli()
        val event = Event(
            id = 9L,
            title = "立刻提醒",
            date = startMillis,
            startTime = startMillis,
            reminder = 0,
        )

        useCase(event)

        assertEquals(1, scheduler.scheduleCalls)
        // triggerAt = startTime - 0 = startTime
        assertEquals(startMillis, scheduler.lastInfo!!.triggerAt.toEpochMilli())
    }

    @Test
    fun `large reminder minutes still produces valid triggerAt`() = runTest {
        val scheduler = CapturingScheduler()
        val useCase = ScheduleEventReminderUseCase(scheduler)

        val startMillis = Instant.parse("2026-12-01T10:00:00Z").toEpochMilli()
        val event = Event(
            id = 10L,
            title = "提前一天",
            date = startMillis,
            startTime = startMillis,
            reminder = 24 * 60, // 一天
        )

        useCase(event)

        assertEquals(startMillis - 24L * 60L * 60_000L, scheduler.lastInfo!!.triggerAt.toEpochMilli())
    }
}