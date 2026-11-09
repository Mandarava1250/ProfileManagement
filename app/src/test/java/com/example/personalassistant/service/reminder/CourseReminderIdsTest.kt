package com.example.personalassistant.service.reminder

import com.example.personalassistant.navigation.DeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 28 P6：课程提醒的标识（闹钟 requestCode / 通知 id / 闹钟 Uri）
 *
 * ## 为什么要测这个
 *
 * 以前标识只含 `courseId + dayOfWeek`：同门课同一天两个课时会**互相覆盖** ——
 * 一个闹钟顶掉另一个（PendingIntent 身份相同），通知也会撞 id（后一条顶掉前一条）。
 *
 * 这里守住三条：
 * 1. 同一天不同课时 → requestCode 不同、通知 id 不同、闹钟 Uri 不同
 * 2. 不同课程 → 标识不同（别跟别的课撞）
 * 3. 标识不溢出（courseId 很大时也不越界）
 */
class CourseReminderIdsTest {

    private val courseId = 42L
    private val day = LocalDate.of(2026, 11, 4) // 周三

    @Test
    fun `同一天不同课时的 requestCode 不重复`() {
        val first = CourseReminderScheduler.requestCodeFor(courseId, day.dayOfWeek.value, 0)
        val second = CourseReminderScheduler.requestCodeFor(courseId, day.dayOfWeek.value, 1)

        assertNotEquals("同门课同一天两个课时必须是两个闹钟", first, second)
    }

    @Test
    fun `同一天不同课时的通知 id 不重复`() {
        val first = CourseReminderScheduler.notificationIdFor(courseId, day, 0)
        val second = CourseReminderScheduler.notificationIdFor(courseId, day, 1)

        assertNotEquals("同门课同一天两个课时不能撞通知 id", first, second)
    }

    @Test
    fun `不同课程的标识不撞`() {
        val mine = CourseReminderScheduler.requestCodeFor(1L, 3, 0)
        val other = CourseReminderScheduler.requestCodeFor(2L, 3, 0)

        assertNotEquals("两门课的闹钟不能是同一个", mine, other)

        val mineNotification = CourseReminderScheduler.notificationIdFor(1L, day, 0)
        val otherNotification = CourseReminderScheduler.notificationIdFor(2L, day, 0)
        assertNotEquals("两门课的通知不能撞 id", mineNotification, otherNotification)
    }

    @Test
    fun `不同星期的标识不撞`() {
        val monday = CourseReminderScheduler.requestCodeFor(courseId, 1, 0)
        val wednesday = CourseReminderScheduler.requestCodeFor(courseId, 3, 0)

        assertNotEquals("不同天的闹钟不能是同一个", monday, wednesday)
    }

    @Test
    fun `课程 id 很大时 requestCode 仍不溢出`() {
        val huge = Long.MAX_VALUE
        val code = CourseReminderScheduler.requestCodeFor(huge, 7, 31)

        assertTrue("requestCode 必须是正数（Int 上界内），实际 $code", code > 0)
        assertNotEquals(
            "取模后不能跟 courseId=0 撞上",
            code,
            CourseReminderScheduler.requestCodeFor(0L, 7, 31),
        )
    }

    @Test
    fun `通知 id 落在合法区间`() {
        val id = CourseReminderScheduler.notificationIdFor(Long.MAX_VALUE, day, 3)

        assertTrue("通知 id 必须为正，实际 $id", id > 0)
        assertTrue(
            "通知 id 不能越过 Int 上界，实际 $id",
            id < Int.MAX_VALUE,
        )
    }

    @Test
    fun `闹钟 Uri 带课时序号且能解析回来`() {
        val first = DeepLinks.courseReminderUri(courseId, day.toEpochDay(), 0)
        val second = DeepLinks.courseReminderUri(courseId, day.toEpochDay(), 1)

        assertNotEquals("Uri 相同 = 同一个闹钟（后者顶掉前者）", first, second)

        val parsed = DeepLinks.parseCourseReminderUri(second)
        assertEquals(courseId, parsed?.courseId)
        assertEquals(day.toEpochDay(), parsed?.epochDay)
        assertEquals(1, parsed?.sessionIndex)
    }

    @Test
    fun `不是课程提醒 Uri 时解析为 null`() {
        assertNull(DeepLinks.parseCourseReminderUri(null))
        assertNull(DeepLinks.parseCourseReminderUri("event://1/2"))
        assertNull(DeepLinks.parseCourseReminderUri("course://1/2"))
        assertNull(DeepLinks.parseCourseReminderUri("course://x/2/0"))
    }
}
