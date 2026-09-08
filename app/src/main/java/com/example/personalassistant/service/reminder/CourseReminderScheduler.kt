package com.example.personalassistant.service.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.personalassistant.core.holiday.usecase.IsHolidayUseCase
import com.example.personalassistant.feature.timetable.domain.model.Course
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * 课程提醒调度器
 *
 *  - 使用 AlarmManager.setExactAndAllowWhileIdle 触发精确闹钟
 *  - 每门课程触发前 N 分钟提醒，N 由 Course.reminderMinutes 决定
 *  - 触发后由 CourseReminderReceiver 接收并发送通知
 *  - WeekFilter：course.appliesToWeek(weekNumber) 在目标周成立才调度
 *
 * 简化策略：
 *  - 仅调度"从今天起到学期结束"内"下一周"的有效课程事件
 *  - 应用启动 / 课程增删改后调用 rescheduleAll 重建闹钟
 *  - 闹钟 requestCode 用 (courseId.toInt() * 10 + dayOfWeek)，同课程同天幂等
 */
class CourseReminderScheduler @Inject constructor(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val isHolidayUseCase: IsHolidayUseCase,
) {

    /**
     * 调度全部课程的提醒（替换现有所有闹钟）
     */
    suspend fun rescheduleAll(
        courses: List<Course>,
        semesterStartMillis: Long?,
    ) {
        if (semesterStartMillis == null) return
        cancelAll(courses)
        val today = LocalDate.now()
        // 调度接下来 4 周内的课程事件
        for (course in courses) {
            if (!course.hasReminder) continue
            for (offset in 0..3) {
                val targetDate = today.plusWeeks(offset.toLong())
                scheduleCourse(course, targetDate, semesterStartMillis)
            }
        }
    }

    /**
     * 调度某门课在某一天的事件
     */
    private fun scheduleCourse(
        course: Course,
        targetDate: LocalDate,
        semesterStartMillis: Long,
    ) {
        // ===== Phase 8 Sub-step 7：节假日避让 =====
        // 用 runBlocking 是因为 AlarmManager 是同步 API；节假日判断是轻量 Room 查询
        if (runBlocking { isHolidayUseCase(targetDate) }) {
            Log.i(TAG, "[HolidayAware] skip course=${course.id} date=$targetDate (HOLIDAY)")
            return
        }

        // 计算 targetDate 是第几周（基于学期开始日期）
        val semesterStart = java.time.Instant.ofEpochMilli(semesterStartMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(semesterStart, targetDate)
        if (daysBetween < 0) return
        val weekNumber = (daysBetween / 7).toInt() + 1
        if (!course.appliesToWeek(weekNumber)) return

        val dayOfWeek = targetDate.dayOfWeek.value  // 1..7
        if (course.dayOfWeek != dayOfWeek) return

        val startLocalTime = LocalTime.of(course.startTime / 60, course.startTime % 60)
        val courseStart = LocalDateTime.of(targetDate, startLocalTime)
        val triggerAt = courseStart.minusMinutes(course.reminderMinutes.toLong())
        if (triggerAt.isBefore(LocalDateTime.now())) return

        val triggerMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = buildPendingIntent(course.id, targetDate, course.name, course.teacher, course.location, course.reminderMinutes)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "setExactAndAllowWhileIdle 被拒：${se.message}")
        }
    }

    /**
     * 取消全部课程提醒
     */
    fun cancelAll(courses: List<Course>) {
        val today = LocalDate.now()
        for (course in courses) {
            for (offset in 0..3) {
                val targetDate = today.plusWeeks(offset.toLong())
                val pi = buildPendingIntent(course.id, targetDate, "", "", "", 0, mutable = false)
                alarmManager.cancel(pi)
            }
        }
    }

    private fun buildPendingIntent(
        courseId: Long,
        targetDate: LocalDate,
        courseName: String,
        teacher: String,
        location: String,
        reminderMinutes: Int,
        mutable: Boolean = true,
    ): PendingIntent {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            action = ACTION_COURSE_REMINDER
            putExtra(EXTRA_COURSE_ID, courseId)
            putExtra(EXTRA_COURSE_NAME, courseName)
            putExtra(EXTRA_COURSE_TEACHER, teacher)
            putExtra(EXTRA_COURSE_LOCATION, location)
            putExtra(EXTRA_REMINDER_MINUTES, reminderMinutes)
            putExtra(EXTRA_TARGET_DATE, targetDate.toEpochDay())
            data = android.net.Uri.parse("course://$courseId/${targetDate.toEpochDay()}")
        }
        val requestCode = (courseId.toInt() and 0x7FFF_FFFF) * 16 + targetDate.dayOfWeek.value
        val flags = if (mutable) (PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    else (PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            flags,
        )
    }

    companion object {
        private const val TAG = "CourseReminder"

        const val ACTION_COURSE_REMINDER = "com.example.personalassistant.action.COURSE_REMINDER"
        const val EXTRA_COURSE_ID = "courseId"
        const val EXTRA_COURSE_NAME = "courseName"
        const val EXTRA_COURSE_TEACHER = "teacher"
        const val EXTRA_COURSE_LOCATION = "location"
        const val EXTRA_REMINDER_MINUTES = "reminderMinutes"
        const val EXTRA_TARGET_DATE = "targetDateEpochDay"

        /** 课程通知 ID 基址，避免与其它模块冲突 */
        const val NOTIFICATION_ID_BASE = 100_000

        fun notificationIdFor(courseId: Long, targetDate: LocalDate): Int =
            NOTIFICATION_ID_BASE + (courseId.toInt() and 0x7FFF_FFFF) + targetDate.toEpochDay().toInt()
    }
}