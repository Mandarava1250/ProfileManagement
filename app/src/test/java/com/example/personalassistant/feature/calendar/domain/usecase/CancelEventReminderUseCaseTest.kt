package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CancelEventReminderUseCase 单测 —— 验证 require(id > 0) + 委托给 scheduler。
 */
class CancelEventReminderUseCaseTest {

    private class CapturingScheduler : ReminderScheduler {
        var lastCancelledId: Long? = null
        var cancelCalls: Int = 0
        override suspend fun schedule(info: ReminderInfo) = Unit
        override suspend fun cancel(eventId: Long) {
            lastCancelledId = eventId
            cancelCalls++
        }
        override suspend fun cancelAll() = Unit
    }

    @Test
    fun `positive eventId is forwarded to scheduler`() = runTest {
        val scheduler = CapturingScheduler()
        val useCase = CancelEventReminderUseCase(scheduler)

        useCase(42L)

        assertEquals(1, scheduler.cancelCalls)
        assertEquals(42L, scheduler.lastCancelledId)
    }

    @Test
    fun `zero eventId throws IllegalArgumentException and does not call scheduler`() = runTest {
        val scheduler = CapturingScheduler()
        val useCase = CancelEventReminderUseCase(scheduler)

        val ex = runCatching { useCase(0L) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException", ex is IllegalArgumentException)
        assertEquals(0, scheduler.cancelCalls)
    }

    @Test
    fun `negative eventId throws IllegalArgumentException`() = runTest {
        val scheduler = CapturingScheduler()
        val useCase = CancelEventReminderUseCase(scheduler)

        val ex = runCatching { useCase(-1L) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException", ex is IllegalArgumentException)
        assertEquals(0, scheduler.cancelCalls)
    }
}