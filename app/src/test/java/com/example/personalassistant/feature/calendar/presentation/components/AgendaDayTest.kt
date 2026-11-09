package com.example.personalassistant.feature.calendar.presentation.components

import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.timetable.domain.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日程流聚合的排序测试。
 *
 * 守住两条约定：
 * 1. 全天事项排在带时刻事项之前
 * 2. 带时刻的事项按开始时刻升序
 *
 * 这跟 DayDetail 里 events 的既有排序一致 —— 日历本来就是全天在上。
 */
class AgendaDayTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 某天的某个时刻（毫秒时间戳）；只给日期时取当天 00:00 */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDate.of(year, month, day)
            .atTime(hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun event(
        id: Long,
        title: String,
        startMillis: Long? = null,
        isAllDay: Boolean = false,
    ) = Event(
        id = id,
        title = title,
        date = at(2026, 11, 4),
        isAllDay = isAllDay,
        startTime = startMillis,
        endTime = startMillis?.plus(60 * 60 * 1000),
    )

    private fun course(
        id: Long,
        name: String,
        startMinute: Int,
        endMinute: Int,
    ) = Course(
        id = id,
        name = name,
        dayOfWeek = 3,
        startTime = startMinute,
        endTime = endMinute,
        color = 0xFF3F7DE0.toInt(),
        semester = "2026-2027-1",
        weeks = null,
    )

    private fun task(id: Long, title: String, dueMillis: Long?) = Task(
        id = id,
        title = title,
        dueDate = dueMillis,
        priority = TaskPriority.MEDIUM,
    )

    private fun detail(
        events: List<Event> = emptyList(),
        tasks: List<Task> = emptyList(),
        courses: List<Course> = emptyList(),
    ) = DayDetail(
        date = at(2026, 11, 4),
        dayOfWeek = 3,
        events = events,
        tasks = tasks,
        courses = courses,
    )

    @Test
    fun `全天事项排在带时刻事项之前`() {
        val day = buildAgendaDay(
            detail(
                events = listOf(
                    event(id = 1, title = "十点会议", startMillis = at(2026, 11, 4, 10)),
                    event(id = 2, title = "团建", isAllDay = true),
                ),
            ),
        )

        assertEquals(2, day.items.size)
        assertEquals("团建", day.items[0].title)
        assertTrue(day.items[0].isAllDay)
        assertEquals("十点会议", day.items[1].title)
    }

    @Test
    fun `带时刻的事项按开始时刻升序`() {
        val day = buildAgendaDay(
            detail(
                events = listOf(
                    event(id = 1, title = "下午", startMillis = at(2026, 11, 4, 15)),
                    event(id = 2, title = "上午", startMillis = at(2026, 11, 4, 9)),
                ),
            ),
        )

        assertEquals("上午", day.items[0].title)
        assertEquals("下午", day.items[1].title)
    }

    @Test
    fun `课程与日程按各自开始时刻混排`() {
        val day = buildAgendaDay(
            detail(
                events = listOf(
                    event(id = 1, title = "十点会议", startMillis = at(2026, 11, 4, 10)),
                ),
                courses = listOf(course(id = 5, name = "高数", startMinute = 8 * 60, endMinute = 9 * 60)),
            ),
        )

        assertEquals(2, day.items.size)
        // 08:00 的课排在 10:00 的日程前面
        assertEquals("高数", day.items[0].title)
        assertEquals(AgendaKind.COURSE, day.items[0].kind)
        assertEquals("十点会议", day.items[1].title)
        assertEquals(AgendaKind.EVENT, day.items[1].kind)
    }

    @Test
    fun `没有截止时刻的任务按全天事项处理`() {
        val day = buildAgendaDay(
            detail(
                tasks = listOf(
                    task(id = 9, title = "交报告", dueMillis = null),
                    task(id = 8, title = "买菜", dueMillis = at(2026, 11, 4, 18)),
                ),
            ),
        )

        assertEquals(2, day.items.size)
        assertEquals("交报告", day.items[0].title)
        assertTrue(day.items[0].isAllDay)
        assertEquals("买菜", day.items[1].title)
    }

    @Test
    fun `三类内容合并后的条数正确`() {
        val day = buildAgendaDay(
            detail(
                events = listOf(event(id = 1, title = "会议", startMillis = at(2026, 11, 4, 10))),
                tasks = listOf(task(id = 2, title = "任务", dueMillis = at(2026, 11, 4, 12))),
                courses = listOf(course(id = 3, name = "课", startMinute = 9 * 60, endMinute = 10 * 60)),
            ),
        )

        assertEquals(3, day.items.size)
        // 三条都带时刻
        assertEquals(3, day.timedCount)
        assertTrue(!day.isEmpty)
    }
}
