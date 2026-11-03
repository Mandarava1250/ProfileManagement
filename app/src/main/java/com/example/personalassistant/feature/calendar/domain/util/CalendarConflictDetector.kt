package com.example.personalassistant.feature.calendar.domain.util

import com.example.personalassistant.feature.calendar.domain.model.CalendarConflictEntry
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.timetable.domain.model.Course
import java.time.Instant
import java.time.ZoneId

/**
 * Phase 20.5：清单项时段 ↔ 课程 的冲突检测
 *
 * ## 判定规则
 *
 * 课程用「当天分钟数」，清单项用「epoch 毫秒」——
 * 这里先把清单项换算成**当天的时段切片**（[CalendarTimeRange]），
 * 再按「区间有交集」判定：`taskStart < courseEnd && taskEnd > courseStart`。
 *
 * 跨午夜的清单项在当天那半截一直画到 24:00（endMinute = 1440），
 * 所以课程只要落在 24:00 之前就会判为冲突 —— 这正是我们要的：
 * 跨夜的事把后半夜占满了。
 *
 * ## 为什么是纯函数
 *
 * 输入都是已经查好的数据（当天的课程 + 当天的带时段清单项），
 * 不碰数据库 —— 所以能直接单测。
 */
object CalendarConflictDetector {

    /**
     * 找出当天的「清单项 ↔ 课程」冲突
     *
     * @param dayStart 当天 00:00 的时间戳
     * @param courses 当天的课程（已按周次规则过滤）
     * @param timedTasks 当天的清单项（带时间段的才算）
     * @param weekNumber 当前学期内的周次（null = 不在学期内，只做展示用）
     */
    fun detect(
        dayStart: Long,
        courses: List<Course>,
        timedTasks: List<Task>,
        weekNumber: Int? = null,
    ): List<CalendarConflictEntry> {
        if (courses.isEmpty() || timedTasks.isEmpty()) return emptyList()
        val dayOfWeek = dayOfWeekOf(dayStart)
        val result = mutableListOf<CalendarConflictEntry>()

        for (task in timedTasks) {
            val segment = CalendarTaskFilter.segmentOn(task, dayStart) ?: continue
            val hits = courses.filter { course ->
                overlaps(segment.startMinute, segment.endMinute, course.startTime, course.endTime)
            }
            if (hits.isEmpty()) continue
            result += CalendarConflictEntry(
                taskId = task.id,
                taskTitle = task.title,
                conflictingCourses = hits,
                weekNumber = weekNumber,
                timeSlice = com.example.personalassistant.feature.calendar.domain.util.EventTimeMapper.TimeSlice(
                    dayOfWeek = dayOfWeek,
                    startMinutes = segment.startMinute,
                    endMinutes = segment.endMinute,
                ),
            )
        }
        return result
    }

    /** 两个闭开区间是否有交集（[aStart, aEnd) × [bStart, bEnd)） */
    private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean =
        aStart < bEnd && aEnd > bStart

    /** 时间戳 → ISO dayOfWeek（1 = 周一 … 7 = 周日） */
    private fun dayOfWeekOf(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().dayOfWeek.value
}
