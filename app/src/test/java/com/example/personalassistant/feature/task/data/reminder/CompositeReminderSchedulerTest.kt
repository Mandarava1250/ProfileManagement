package com.example.personalassistant.feature.task.data.reminder

import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * CompositeReminderScheduler 单测 —— 验证路由策略正确：
 * - 延迟 ≤ 1 小时：走 AlarmManager
 * - 延迟 > 1 小时：走 WorkManager
 * - 过期：双分支都不调
 * - cancel/cancelAll：双分支都调
 */
class CompositeReminderSchedulerTest {

    private class FakeScheduler(val name: String) : ReminderScheduler {
        var scheduleCalls = 0
        var cancelCalls = 0
        var cancelAllCalls = 0
        var lastScheduled: ReminderInfo? = null
        var lastCancelled: Long? = null

        override suspend fun schedule(info: ReminderInfo) {
            scheduleCalls++
            lastScheduled = info
        }

        override suspend fun cancel(taskId: Long) {
            cancelCalls++
            lastCancelled = taskId
        }

        override suspend fun cancelAll() {
            cancelAllCalls++
        }
    }

    private fun info(triggerAt: Instant, taskId: Long = 1L): ReminderInfo =
        ReminderInfo(
            taskId = taskId,
            title = "Test",
            body = "Body",
            triggerAt = triggerAt,
        )

    @Test
    fun `short delay routes to AlarmManager`() = runBlocking {
        val alarm = FakeScheduler("alarm")
        val wm = FakeScheduler("wm")
        val composite = CompositeReminderScheduler(wm, alarm)
        val now = Instant.now()
        val trigger = now.plus(Duration.ofMinutes(30)) // 30 分钟 → 短延迟

        composite.schedule(info(trigger))

        assertEquals(1, alarm.scheduleCalls)
        assertEquals(0, wm.scheduleCalls)
        assertEquals(trigger, alarm.lastScheduled?.triggerAt)
    }

    @Test
    fun `long delay routes to WorkManager`() = runBlocking {
        val alarm = FakeScheduler("alarm")
        val wm = FakeScheduler("wm")
        val composite = CompositeReminderScheduler(wm, alarm)
        val now = Instant.now()
        val trigger = now.plus(Duration.ofHours(5)) // 5 小时 → 长延迟

        composite.schedule(info(trigger))

        assertEquals(1, wm.scheduleCalls)
        assertEquals(0, alarm.scheduleCalls)
        assertEquals(trigger, wm.lastScheduled?.triggerAt)
    }

    @Test
    fun `delay exactly equal to threshold routes to AlarmManager`() = runBlocking {
        // 阈值 = 1 小时；正好 = 阈值时按代码 <= 判断走 AlarmManager
        val alarm = FakeScheduler("alarm")
        val wm = FakeScheduler("wm")
        val composite = CompositeReminderScheduler(wm, alarm)
        val now = Instant.now()
        val trigger = now.plus(CompositeReminderScheduler.SHORT_DELAY_THRESHOLD)

        composite.schedule(info(trigger))

        assertEquals(1, alarm.scheduleCalls)
        assertEquals(0, wm.scheduleCalls)
    }

    @Test
    fun `delay one minute over threshold routes to WorkManager`() = runBlocking {
        val alarm = FakeScheduler("alarm")
        val wm = FakeScheduler("wm")
        val composite = CompositeReminderScheduler(wm, alarm)
        val now = Instant.now()
        val trigger = now.plus(CompositeReminderScheduler.SHORT_DELAY_THRESHOLD).plus(Duration.ofMinutes(1))

        composite.schedule(info(trigger))

        assertEquals(1, wm.scheduleCalls)
        assertEquals(0, alarm.scheduleCalls)
    }

    @Test
    fun `past triggerAt does not call either branch`() = runBlocking {
        val alarm = FakeScheduler("alarm")
        val wm = FakeScheduler("wm")
        val composite = CompositeReminderScheduler(wm, alarm)
        val now = Instant.now()
        val trigger = now.minus(Duration.ofMinutes(10)) // 已过期

        composite.schedule(info(trigger))

        assertEquals(0, alarm.scheduleCalls)
        assertEquals(0, wm.scheduleCalls)
    }

    @Test
    fun `triggerAt equal to now is treated as past`() = runBlocking {
        // 即时或已过：双分支都不调
        val alarm = FakeScheduler("alarm")
        val wm = FakeScheduler("wm")
        val composite = CompositeReminderScheduler(wm, alarm)
        val now = Instant.now()

        composite.schedule(info(now))

        assertEquals(0, alarm.scheduleCalls)
        assertEquals(0, wm.scheduleCalls)
    }

    @Test
    fun `cancel calls both branches with same taskId`() = runBlocking {
        val alarm = FakeScheduler("alarm")
        val wm = FakeScheduler("wm")
        val composite = CompositeReminderScheduler(wm, alarm)

        composite.cancel(taskId = 42L)

        assertEquals(1, wm.cancelCalls)
        assertEquals(1, alarm.cancelCalls)
        assertEquals(42L, wm.lastCancelled)
        assertEquals(42L, alarm.lastCancelled)
    }

    @Test
    fun `cancelAll calls both branches`() = runBlocking {
        val alarm = FakeScheduler("alarm")
        val wm = FakeScheduler("wm")
        val composite = CompositeReminderScheduler(wm, alarm)

        composite.cancelAll()

        assertEquals(1, wm.cancelAllCalls)
        assertEquals(1, alarm.cancelAllCalls)
    }

    @Test
    fun `threshold constant is exactly one hour`() {
        // Phase 5 设计约束：阈值是 1 小时。
        // 若将来调整需要同步更新这个测试。
        assertEquals(Duration.ofHours(1), CompositeReminderScheduler.SHORT_DELAY_THRESHOLD)
    }

    @Test
    fun `multiple schedules accumulate counts independently`() = runBlocking {
        val alarm = FakeScheduler("alarm")
        val wm = FakeScheduler("wm")
        val composite = CompositeReminderScheduler(wm, alarm)
        val now = Instant.now()

        composite.schedule(info(now.plus(Duration.ofMinutes(10)), taskId = 1L))
        composite.schedule(info(now.plus(Duration.ofMinutes(20)), taskId = 2L))
        composite.schedule(info(now.plus(Duration.ofHours(2)), taskId = 3L))
        composite.schedule(info(now.plus(Duration.ofDays(1)), taskId = 4L))

        assertEquals(2, alarm.scheduleCalls)
        assertEquals(2, wm.scheduleCalls)
        assertTrue("task 1 should be scheduled via alarm", alarm.lastScheduled?.taskId == 2L)
        assertTrue("task 4 should be scheduled via wm", wm.lastScheduled?.taskId == 4L)
    }
}
