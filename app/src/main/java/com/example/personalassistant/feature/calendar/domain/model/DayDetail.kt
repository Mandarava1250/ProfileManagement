package com.example.personalassistant.feature.calendar.domain.model

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.timetable.domain.model.Course

/**
 * Phase 7c：单日联动详情
 *
 * 月视图点击日期 → 弹出的 BottomSheet 里展示的「当天内容」聚合。
 *
 * @param date 当天 00:00 时间戳
 * @param dayOfWeek 1=周一, 7=周日（派生自 date）
 * @param events 当日 [com.example.personalassistant.feature.calendar.domain.model.Event] 列表
 *               （已按 isAllDay DESC, startTime ASC 排序）
 * @param tasks 当日 [Task] 列表（按 priority DESC, dueDate ASC）
 *               **含已完成任务** —— 日历里勾完的任务要留着，用户手动删除才消失
 * @param courses 当日 [Course] 列表（按 startTime ASC，已过滤周次规则）
 * @param weekNumber 当前学期内的周次（null 表示无学期信息）
 *
 * 涟宝解读：
 * - events 来源：CalendarRepository.observeEventsByDate(date)
 * - tasks 来源：TaskDao.observeBetweenIncludingCompleted(dayStart, dayEnd-1)（含已完成；区间为半开 [dayStart, 次日 00:00)）
 * - courses 来源：CourseDao.observeByDayOfWeek(dayOfWeek) + 客户端过滤 appliesToWeek(weekNumber)
 * - 三个流在 Repository 里 combine 成 [DayDetail] 后再发出去
 */
data class DayDetail(
    val date: Long,
    val dayOfWeek: Int,
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val courses: List<Course> = emptyList(),
    /**
     * Phase 24B：当天所有习惯的打卡情况（未归档习惯）
     *
     * 打卡 / 撤销都落在某一天上，所以按天存 —— 日视图、月视图、周视图共用同一份。
     */
    val habits: List<CalendarHabitItem> = emptyList(),
    val weekNumber: Int? = null,
) {
    /** 该日是否有任意内容 */
    val hasAny: Boolean
        get() = events.isNotEmpty() || tasks.isNotEmpty() || courses.isNotEmpty() || habits.isNotEmpty()

    /** 总条目数 */
    val totalCount: Int get() = events.size + tasks.size + courses.size + habits.size
}