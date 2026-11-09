package com.example.personalassistant.feature.calendar.presentation.components

import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.DayDetail

/**
 * 日程流的一行。
 *
 * 日程、课程、任务三类内容被拍平成同一种行 —— 它们在用户眼里都是「那天要做的事」，
 * 混在一条时间线上比分成三堆更好读。
 */
data class AgendaItem(
    /** 稳定 key（同一条内容多次重组不会跳） */
    val key: String,
    val kind: AgendaKind,
    val title: String,
    /** 时刻文本（"09:00"）；全天事项为 null */
    val timeText: String?,
    /** 次要信息：分类名 / 地点 / 教师 */
    val subtitle: String?,
    /** ARGB 色值；只有 EVENT / COURSE 有意义，任务由 UI 给品牌色 */
    val color: Int,
    val isCompleted: Boolean = false,
    /** 全天事项排在带时刻的事项之前 */
    val isAllDay: Boolean = false,
    /** 排序用：当天分钟数；全天事项用 -1 */
    val sortMinute: Int,
)

/** 这一行来自哪一类内容 */
enum class AgendaKind { EVENT, COURSE, TASK }

/**
 * 日程流里的一天。
 *
 * @param date 当天 00:00 时间戳
 * @param dayOfWeek 1=周一 … 7=周日
 */
data class AgendaDay(
    val date: Long,
    val dayOfWeek: Int,
    val items: List<AgendaItem>,
    /**
     * 这一天的原始详情。
     *
     * 点某一行时要拿到 Event / Task / Course 本体（比如日程要打开编辑面板），
     * 光有 key 里的 id 不够 —— 事件编辑需要完整对象。
     */
    val detail: com.example.personalassistant.feature.calendar.domain.model.DayDetail? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    /** 带时刻的事项数 —— 头部文案用「今天 N 件事」 */
    val timedCount: Int get() = items.count { !it.isAllDay }
}

/**
 * 把一天的三类内容合并成一条时间线。
 *
 * ## 排序约定
 *
 * 全天事项排在带时刻事项之前，其余按开始时刻升序。
 * 这跟 [DayDetail] 里 events 的既有排序（isAllDay DESC, startTime ASC）一致 ——
 * 日历里原本就是全天在上、按时刻往下，聚合流沿用同一套，用户不用重新学。
 *
 * @param dayDetail 当天的聚合详情（日程 + 任务 + 课程）
 */
fun buildAgendaDay(dayDetail: DayDetail): AgendaDay {
    val items = mutableListOf<AgendaItem>()

    // 日程
    dayDetail.events.forEach { event ->
        val minute = if (event.isAllDay) null else event.startTime?.let { minuteOfDay(it) }
        items += AgendaItem(
            key = "event-${event.id}",
            kind = AgendaKind.EVENT,
            title = event.title,
            timeText = minute?.let { DateUtils.formatMinutesOfDay(it) },
            subtitle = event.category,
            color = event.color,
            isAllDay = event.isAllDay,
            sortMinute = minute ?: -1,
        )
    }

    // 课程
    dayDetail.courses.forEach { course ->
        items += AgendaItem(
            key = "course-${course.id}",
            kind = AgendaKind.COURSE,
            title = course.name,
            timeText = DateUtils.formatMinutesOfDay(course.startTime),
            subtitle = listOfNotNull(course.location, course.teacher)
                .joinToString(" · ")
                .ifBlank { null },
            color = course.color,
            sortMinute = course.startTime,
        )
    }

    // 任务
    dayDetail.tasks.forEach { task ->
        // 没有截止时刻的任务当成全天事项 —— 它不占时间轴上的位置
        val minute = task.dueDate?.let { minuteOfDay(it) }
        items += AgendaItem(
            key = "task-${task.id}",
            kind = AgendaKind.TASK,
            title = task.title,
            timeText = minute?.let { DateUtils.formatMinutesOfDay(it) },
            subtitle = task.category,
            color = 0,
            isCompleted = task.isCompleted,
            isAllDay = minute == null,
            sortMinute = minute ?: -1,
        )
    }

    return AgendaDay(
        date = dayDetail.date,
        dayOfWeek = dayDetail.dayOfWeek,
        items = items.sortedWith(
            compareByDescending<AgendaItem> { it.isAllDay }.thenBy { it.sortMinute },
        ),
        detail = dayDetail,
    )
}
