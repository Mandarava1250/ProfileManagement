package com.example.personalassistant.service.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.personalassistant.core.holiday.usecase.IsHolidayUseCase
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import com.example.personalassistant.feature.timetable.domain.model.resolvedLocation
import com.example.personalassistant.feature.timetable.domain.model.resolvedTeacher
import com.example.personalassistant.navigation.DeepLinks
import dagger.hilt.android.qualifiers.ApplicationContext
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
 *
 * ## Phase 28 P6：一门课多个课时 → 每个课时一个闹钟
 *
 * 以前标识只含 `courseId + dayOfWeek`，同门课同一天两个课时会**互相覆盖**：
 * 一个闹钟顶掉另一个（PendingIntent 的 requestCode / data 相同 = 同一个闹钟），
 * 通知也会撞 id（后一条把前一条顶掉）。
 *
 * 现在标识里多了**课时的序号**（当天第几个课时）：
 * - 闹钟 requestCode：[requestCodeFor]
 * - 通知 Uri：`course://<courseId>/<epochDay>/<sessionIndex>`（见 [DeepLinks]）
 * - 通知 id：[notificationIdFor]
 *
 * 三处都带上课时序号，同门课同一天的几个课时才不会互相顶。
 */
class CourseReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
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
     * 调度某门课在某一天的事件（**逐课时**排提醒）
     *
     * 节假日避让与周次过滤是「按天」的，所以放在外层；
     * 每个课时各自算触发时刻、各自排一个闹钟。
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

        // Phase 28 P6：这门课在这一天的**所有课时**逐个排提醒
        val daySessions = course.sessionsOnDate(targetDate)
        daySessions.forEachIndexed { index, session ->
            scheduleSession(course, session, index, targetDate)
        }
    }

    /**
     * 排一个课时的提醒
     *
     * @param sessionIndex 该课时是「这一天第几个课时」（0 起）—— 标识里带上它，
     *   同门课同一天的几个课时才不会互相顶
     */
    private fun scheduleSession(
        course: Course,
        session: CourseSession,
        sessionIndex: Int,
        targetDate: LocalDate,
    ) {
        val startLocalTime = LocalTime.of(session.startTime / 60, session.startTime % 60)
        val courseStart = LocalDateTime.of(targetDate, startLocalTime)
        val triggerAt = courseStart.minusMinutes(course.reminderMinutes.toLong())
        if (triggerAt.isBefore(LocalDateTime.now())) return

        val triggerMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = buildPendingIntent(
            courseId = course.id,
            sessionIndex = sessionIndex,
            targetDate = targetDate,
            courseName = course.name,
            // Phase 28 P10：通知里的教师 / 地点用**这个课时**的值（没有就回落课程级）
            teacher = session.resolvedTeacher(course.teacher),
            location = session.resolvedLocation(course.location),
            reminderMinutes = course.reminderMinutes,
        )

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
     *
     * ⚠️ 必须跟 [scheduleCourse] 一样**逐课时**取消 ——
     * PendingIntent 的身份 = (requestCode, action, data, …)，
     * 少了课时序号就对不上那个闹钟（FLAG_NO_CREATE 时 cancel 什么也不会发生）。
     */
    fun cancelAll(courses: List<Course>) {
        val today = LocalDate.now()
        for (course in courses) {
            for (offset in 0..3) {
                val targetDate = today.plusWeeks(offset.toLong())
                course.sessionsOnDate(targetDate).forEachIndexed { index, _ ->
                    val pi = buildPendingIntent(
                        courseId = course.id,
                        sessionIndex = index,
                        targetDate = targetDate,
                        courseName = "",
                        teacher = "",
                        location = "",
                        reminderMinutes = 0,
                        mutable = false,
                    )
                    alarmManager.cancel(pi)
                }
            }
        }
    }

    private fun buildPendingIntent(
        courseId: Long,
        sessionIndex: Int,
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
            putExtra(EXTRA_SESSION_INDEX, sessionIndex)
            // Uri 里也带课时序号 —— data 是 PendingIntent 身份的一部分，
            // 少了它同门课同一天的两个课时会被当成同一个闹钟（后者顶掉前者）。
            data = android.net.Uri.parse(
                DeepLinks.courseReminderUri(courseId, targetDate.toEpochDay(), sessionIndex),
            )
        }
        val requestCode = requestCodeFor(courseId, targetDate.dayOfWeek.value, sessionIndex)
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

        /** Phase 28 P6：当天第几个课时（0 起） */
        const val EXTRA_SESSION_INDEX = "sessionIndex"

        /** 课程通知 ID 基址，避免与其它模块冲突 */
        const val NOTIFICATION_ID_BASE = 100_000

        // ===== 标识计算（Phase 28 P6）=====

        /** 每门课每天最多记 32 个课时（课时序号占 5 bit） */
        private const val SESSION_SLOTS = 32

        /** 7 天 × 32 个课时 */
        private const val DAY_SLOTS = 7 * SESSION_SLOTS

        /** 课程 id 取模（避免 courseId × 常数 溢出 Int） */
        private const val COURSE_KEY_MOD = 4_194_304L

        // ===== 通知 id 的位段（Phase 28 P9）=====

        /**
         * 课时序号占 **5 bit**（0..31）
         *
         * 与 [SESSION_SLOTS] 对齐 —— 一门课一天超过 32 个课时才会绕回（现实中不可能）。
         */
        private const val SESSION_BITS = 5

        /**
         * 课程 id 占 **12 bit**（0..4095）
         *
         * 个人课表的课程数（Room 自增 id）远远到不了 4096。
         */
        private const val COURSE_BITS = 12

        /**
         * 日期占 **13 bit**（0..8191 天 ≈ 22.4 年）
         *
         * 基准日 [NOTIFICATION_EPOCH_DAY] 是 2024-01-01，8192 天后到 2046 年年中 ——
         * 够覆盖任何 realistically 会跑起来的安装周期。
         */
        private const val DAY_BITS = 13

        /** 日期位段能表示的天数 */
        private const val DAY_SLOTS_RANGE = 1 shl DAY_BITS

        /** 课程位段能表示的课程数 */
        private const val COURSE_SLOTS_RANGE = 1 shl COURSE_BITS

        /** 日期位段的基准日：2024-01-01（epochDay） */
        private const val NOTIFICATION_EPOCH_DAY = 19_723L

        /**
         * 闹钟 requestCode
         *
         * 组成：`courseKey × (7 × 32) + (星期几 - 1) × 32 + 课时序号`
         *
         * - 课程部分先取模（[COURSE_KEY_MOD]）→ `4_194_304 × 224 ≈ 9.4 亿`，**不会溢出 Int**
         * - 课时序号占 5 bit（0..31）—— 一门课一天超过 32 个课时才会撞（现实中不可能）
         *
         * ⚠️ requestCode **不是**闹钟的唯一标识 —— PendingIntent 的身份还包括 `data`，
         * 而这里的 `data`（闹钟 Uri）里带着**完整日期 + 课时序号**。
         * 所以不同日期的同一个闹钟也不会互相覆盖。
         *
         * @param dayOfWeek 1=周一 … 7=周日
         * @param sessionIndex 当天第几个课时（0 起）
         */
        fun requestCodeFor(courseId: Long, dayOfWeek: Int, sessionIndex: Int): Int {
            val courseKey = bounded(courseId, COURSE_KEY_MOD)
            val slot = (dayOfWeek.coerceIn(1, 7) - 1) * SESSION_SLOTS +
                sessionIndex.coerceIn(0, SESSION_SLOTS - 1)
            return (courseKey * DAY_SLOTS + slot).toInt()
        }

        /**
         * 通知 id —— **三个字段按位段拼进一个 Int**（Phase 28 P9）
         *
         * ## 位段布局（低位 → 高位）
         *
         * ```
         * ┌────────────────────┬──────────────┬───────────────┐
         * │ 日期 (13 bit)      │ 课程 (12 bit)│ 课时 (5 bit)  │
         * └────────────────────┴──────────────┴───────────────┘
         * ```
         *
         * ## 为什么不再取模
         *
         * 旧实现是 `courseId % 4_194_304` + `epochDay % 8_192` 再乘加 ——
         * **取模必然产生别名**：两门 id 差 4_194_304 的课会拿到同一个通知 id，
         * 后弹的通知把先弹的顶掉，用户只看到一条。
         *
         * 改成按位段拼之后，**同一个位段里取值不同 ⇒ 拼出来的 id 一定不同**，
         * 不靠「概率上不撞」：
         * - 不同课程 → 课程位段不同 → id 不同
         * - 同一天不同课时 → 课时位段不同 → id 不同
         * - 不同日期 → 日期位段不同 → id 不同
         *
         * ## 取值范围（写死在这里，别偷偷改）
         *
         * - 课程数 ≤ 4096（个人课表远远够用）
         * - 日期跨度 ≤ 8192 天 ≈ 22.4 年（基准日 2024-01-01，即到 2046 年年中）
         * - 每天每门课 ≤ 32 个课时
         *
         * 超出范围会**绕回**（绕回后与 N 年前的旧 id 相同）—— 但提醒只排未来 4 周，
         * 那时旧通知早就不在托盘里了。
         *
         * @param sessionIndex 当天第几个课时（0 起）
         */
        fun notificationIdFor(courseId: Long, targetDate: LocalDate, sessionIndex: Int): Int {
            val dayOffset = bounded(targetDate.toEpochDay() - NOTIFICATION_EPOCH_DAY, DAY_SLOTS_RANGE.toLong())
            val courseKey = bounded(courseId, COURSE_SLOTS_RANGE.toLong())
            val slot = sessionIndex.coerceIn(0, SESSION_SLOTS - 1)
            val packed = (dayOffset shl (COURSE_BITS + SESSION_BITS)) or
                (courseKey shl SESSION_BITS) or
                slot.toLong()
            return NOTIFICATION_ID_BASE + packed.toInt()
        }

        /** 取模并保证非负（负 id 也要落在合法区间里） */
        private fun bounded(value: Long, mod: Long): Long = ((value % mod) + mod) % mod
    }
}

/**
 * 这门课在指定日期的**全部课时**（按开始时间排序）
 *
 * - 有课时记录 → 过滤出那一天的那些
 * - 没有课时记录（老数据 / 旧备份导入）→ [Course.effectiveSessions] 用主时段镜像兜底出一个
 */
private fun Course.sessionsOnDate(targetDate: LocalDate): List<CourseSession> =
    effectiveSessions
        .filter { it.dayOfWeek == targetDate.dayOfWeek.value }
        .sortedBy { it.startTime }
