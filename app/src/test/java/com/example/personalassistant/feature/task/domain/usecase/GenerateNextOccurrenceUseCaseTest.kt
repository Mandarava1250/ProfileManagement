package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.RepeatRule
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * GenerateNextOccurrenceUseCase 的纯逻辑测试
 *
 * 覆盖：
 * - 任务无 dueDate → 返回 null
 * - 规则为 None → 返回 null
 * - 正常推进（Daily/Weekly 等）
 * - repeatEndDate 兜底（next 超出 end → 返回 null）
 */
class GenerateNextOccurrenceUseCaseTest {

    private val useCase = GenerateNextOccurrenceUseCase()
    private val zone: ZoneId = ZoneOffset.UTC

    private fun taskWithDue(
        dueDate: LocalDate,
        repeatEndDate: LocalDate? = null,
    ): Task = Task(
        id = 1L,
        title = "测试任务",
        dueDate = dueDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        priority = TaskPriority.MEDIUM,
        repeatEndDate = repeatEndDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
    )

    @Test
    fun `returns null when task has no dueDate`() {
        val task = Task(id = 1L, title = "no due", dueDate = null)
        assertNull(useCase(task, RepeatRule.Daily, zone))
    }

    @Test
    fun `returns null when rule is None`() {
        val task = taskWithDue(LocalDate.of(2026, 9, 7))
        assertNull(useCase(task, RepeatRule.None, zone))
    }

    @Test
    fun `Daily returns next day`() {
        val task = taskWithDue(LocalDate.of(2026, 9, 7))
        val next = useCase(task, RepeatRule.Daily, zone)
        assertEquals(LocalDate.of(2026, 9, 8), next)
    }

    @Test
    fun `Weekly returns next week`() {
        val task = taskWithDue(LocalDate.of(2026, 9, 7))
        val next = useCase(task, RepeatRule.Weekly, zone)
        assertEquals(LocalDate.of(2026, 9, 14), next)
    }

    @Test
    fun `Monthly handles month-end fallback`() {
        val task = taskWithDue(LocalDate.of(2026, 1, 31))
        val next = useCase(task, RepeatRule.Monthly, zone)
        assertEquals(LocalDate.of(2026, 2, 28), next)
    }

    @Test
    fun `CustomWeekdays Mon Wed Fri returns next Wednesday from Monday`() {
        val task = taskWithDue(LocalDate.of(2026, 9, 7)) // 周一
        val rule = RepeatRule.CustomWeekdays(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )
        val next = useCase(task, rule, zone)
        assertEquals(LocalDate.of(2026, 9, 9), next) // 周三
    }

    @Test
    fun `returns null when next is after repeatEndDate`() {
        // dueDate = 2026-09-07, Daily, endDate = 2026-09-08
        // 正常 next 是 2026-09-08；正好等于 endDate 应该是允许的（!isAfter）
        val task = taskWithDue(LocalDate.of(2026, 9, 7), repeatEndDate = LocalDate.of(2026, 9, 8))
        val next = useCase(task, RepeatRule.Daily, zone)
        assertEquals(LocalDate.of(2026, 9, 8), next)

        // 改成 endDate = 2026-09-07，应该返回 null
        val task2 = taskWithDue(LocalDate.of(2026, 9, 7), repeatEndDate = LocalDate.of(2026, 9, 7))
        assertNull(useCase(task2, RepeatRule.Daily, zone))
    }

    @Test
    fun `returns null when next is way past repeatEndDate`() {
        val task = taskWithDue(LocalDate.of(2026, 9, 7), repeatEndDate = LocalDate.of(2026, 9, 10))
        // Weekly → next = 2026-09-14 > 2026-09-10
        assertNull(useCase(task, RepeatRule.Weekly, zone))
    }
}
