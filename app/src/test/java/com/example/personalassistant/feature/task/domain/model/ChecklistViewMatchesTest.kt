package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 17：智能清单谓词单测
 *
 * 视图过滤是纯函数（日期由调用方传入），所以这里能确定性地覆盖边界。
 */
class ChecklistViewMatchesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 2)

    /** 把「当天 10:00」转成毫秒时间戳 */
    private fun at(date: LocalDate, hour: Int = 10): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        listId: Long = TaskList.INBOX_ID,
        dueDate: Long? = null,
        isCompleted: Boolean = false,
    ) = Task(
        id = 1L,
        title = "测试项",
        isCompleted = isCompleted,
        dueDate = dueDate,
        listId = listId,
    )

    // ===== 收集箱 =====

    @Test
    fun `收集箱只收 listId 等于 1 的任务`() {
        val view = ChecklistView.Smart(SmartList.INBOX)
        assertTrue(view.matches(task(listId = 1L), today, zone))
        assertFalse(view.matches(task(listId = 2L), today, zone))
    }

    @Test
    fun `收集箱不看完成状态 —— 已完成的也在收集箱里`() {
        val view = ChecklistView.Smart(SmartList.INBOX)
        assertTrue(view.matches(task(isCompleted = true), today, zone))
    }

    // ===== 今天 =====

    @Test
    fun `今天只收当天到期的`() {
        val view = ChecklistView.Smart(SmartList.TODAY)
        assertTrue(view.matches(task(dueDate = at(today)), today, zone))
        assertFalse(view.matches(task(dueDate = at(today.plusDays(1))), today, zone))
        assertFalse(view.matches(task(dueDate = at(today.minusDays(1))), today, zone))
    }

    @Test
    fun `没有截止时间的任务不属于今天`() {
        val view = ChecklistView.Smart(SmartList.TODAY)
        assertFalse(view.matches(task(dueDate = null), today, zone))
    }

    @Test
    fun `当天的任何时刻都算今天 —— 23 点 59 分也算`() {
        val view = ChecklistView.Smart(SmartList.TODAY)
        assertTrue(view.matches(task(dueDate = at(today, hour = 23)), today, zone))
    }

    // ===== 明天 =====

    @Test
    fun `明天只收次日到期的`() {
        val view = ChecklistView.Smart(SmartList.TOMORROW)
        assertTrue(view.matches(task(dueDate = at(today.plusDays(1))), today, zone))
        assertFalse(view.matches(task(dueDate = at(today)), today, zone))
    }

    // ===== 最近 7 天 =====

    @Test
    fun `最近 7 天含今天到第 7 天`() {
        val view = ChecklistView.Smart(SmartList.NEXT_7_DAYS)
        assertTrue(view.matches(task(dueDate = at(today)), today, zone))
        assertTrue(view.matches(task(dueDate = at(today.plusDays(7))), today, zone))
    }

    @Test
    fun `最近 7 天不含第 8 天与过去`() {
        val view = ChecklistView.Smart(SmartList.NEXT_7_DAYS)
        assertFalse(view.matches(task(dueDate = at(today.plusDays(8))), today, zone))
        assertFalse(view.matches(task(dueDate = at(today.minusDays(1))), today, zone))
    }

    // ===== 全部 / 已完成 =====

    @Test
    fun `全部不过滤`() {
        val view = ChecklistView.Smart(SmartList.ALL)
        assertTrue(view.matches(task(listId = 99L, dueDate = null, isCompleted = true), today, zone))
    }

    @Test
    fun `已完成只看完成状态`() {
        val view = ChecklistView.Smart(SmartList.COMPLETED)
        assertTrue(view.matches(task(isCompleted = true), today, zone))
        assertFalse(view.matches(task(isCompleted = false), today, zone))
    }

    // ===== 用户清单 =====

    @Test
    fun `用户清单只收该清单的任务`() {
        val view = ChecklistView.User(7L)
        assertTrue(view.matches(task(listId = 7L), today, zone))
        assertFalse(view.matches(task(listId = 8L), today, zone))
    }

    @Test
    fun `用户清单不看完成状态与截止日期`() {
        val view = ChecklistView.User(7L)
        assertTrue(
            view.matches(
                task(listId = 7L, dueDate = null, isCompleted = true),
                today,
                zone,
            )
        )
    }
}
