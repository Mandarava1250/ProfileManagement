package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.core.holiday.usecase.IsHolidayUseCase
import com.example.personalassistant.feature.task.data.repository.FakeTaskRepository
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Phase 22.7：重建全部任务提醒
 */
class RebuildTaskRemindersUseCaseTest {

    /** 记录调度过哪些提醒 */
    private class RecordingScheduler : ReminderScheduler {
        val scheduled = mutableListOf<ReminderInfo>()
        var cancelAllCount = 0

        override suspend fun schedule(reminder: ReminderInfo) {
            scheduled += reminder
        }

        override suspend fun cancel(taskId: Long) = Unit

        override suspend fun cancelAll() {
            cancelAllCount++
        }
    }

    private val now = 1_700_000_000_000L

    private fun task(
        id: Long,
        reminderTime: Long?,
        isCompleted: Boolean = false,
    ) = Task(
        id = id,
        title = "任务 $id",
        isCompleted = isCompleted,
        reminderTime = reminderTime,
    )

    @Test
    fun `未来的提醒会被重建`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = RecordingScheduler()
        repo.tasksWithReminders = listOf(task(1L, reminderTime = now + 60_000L))

        val useCase = RebuildTaskRemindersUseCase(repo, fakeScheduleUseCase(scheduler))
        val count = useCase(now)

        assertEquals(1, count)
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(1L, scheduler.scheduled.first().taskId)
    }

    @Test
    fun `提醒时间已经过去的会被跳过`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = RecordingScheduler()
        repo.tasksWithReminders = listOf(task(1L, reminderTime = now - 1_000L))

        val useCase = RebuildTaskRemindersUseCase(repo, fakeScheduleUseCase(scheduler))
        val count = useCase(now)

        assertEquals("过期提醒不该重排", 0, count)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `没有提醒时间的任务被跳过`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = RecordingScheduler()
        repo.tasksWithReminders = listOf(task(1L, reminderTime = null))

        val useCase = RebuildTaskRemindersUseCase(repo, fakeScheduleUseCase(scheduler))
        val count = useCase(now)

        assertEquals(0, count)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `多条提醒全部重建`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = RecordingScheduler()
        repo.tasksWithReminders = listOf(
            task(1L, reminderTime = now + 60_000L),
            task(2L, reminderTime = now + 120_000L),
            task(3L, reminderTime = now + 180_000L),
        )

        val useCase = RebuildTaskRemindersUseCase(repo, fakeScheduleUseCase(scheduler))
        val count = useCase(now)

        assertEquals(3, count)
        assertEquals(listOf(1L, 2L, 3L), scheduler.scheduled.map { it.taskId })
    }

    @Test
    fun `触发时刻就是任务自己的 reminderTime`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = RecordingScheduler()
        val triggerAt = now + 60_000L
        repo.tasksWithReminders = listOf(task(1L, reminderTime = triggerAt))

        val useCase = RebuildTaskRemindersUseCase(repo, fakeScheduleUseCase(scheduler))
        useCase(now)

        assertEquals(
            triggerAt,
            scheduler.scheduled.first().triggerAt.toEpochMilli(),
        )
    }

    @Test
    fun `空列表时什么都不做`() = runTest {
        val repo = FakeTaskRepository()
        val scheduler = RecordingScheduler()

        val useCase = RebuildTaskRemindersUseCase(repo, fakeScheduleUseCase(scheduler))
        val count = useCase(now)

        assertEquals(0, count)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    /**
     * 造一个 [ScheduleReminderUseCase] 的替身：绕过节假日判定，直接转发到 scheduler。
     *
     * 覆写 `invoke` 而不是真的走父类实现 —— 父类里有 `android.util.Log`，
     * 在 JVM 单元测试里会炸（未 mock 的 Android API）。
     */
    private fun fakeScheduleUseCase(scheduler: ReminderScheduler): ScheduleReminderUseCase =
        object : ScheduleReminderUseCase(
            scheduler = scheduler,
            isHolidayUseCase = object : IsHolidayUseCase {
                override suspend fun invoke(date: LocalDate): Boolean = false
            },
        ) {
            override suspend fun invoke(task: Task) {
                val triggerAt = task.reminderTime ?: return
                scheduler.schedule(
                    ReminderInfo(
                        taskId = task.id,
                        title = task.title,
                        body = task.description.orEmpty(),
                        triggerAt = Instant.ofEpochMilli(triggerAt),
                    ),
                )
            }
        }
}
