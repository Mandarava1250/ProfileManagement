package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.components.AppCard
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.presentation.WeekDayColumn
import com.example.personalassistant.feature.calendar.presentation.WeekTimeline
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.timetable.domain.model.Course
import java.time.Instant
import java.time.ZoneId

/**
 * 周视图 = 一周导航 + 选中日详情（Phase 14）
 *
 * 伙伴要的结构（从上到下）：
 *
 * 1. **日期导航栏**：一周七天（几号 + 周一~周日），点一下切到那天
 * 2. **当天任务 / 计划**：可直接勾选完成
 * 3. **课程安排**：当天有几节课、几点到几点
 * 4. **时间轴**：1 小时行轴，课程、事件、任务各就各位
 *
 * 为什么不是 7 列大网格：7 列挤在 360dp 上，每列只有 40dp 出头，
 * 课程名根本写不下。改成「选一天 + 看一天」，每条信息都读得完整。
 */
@Composable
fun WeekCalendarView(
    timeline: WeekTimeline,
    dayDetail: DayDetail?,
    selectedDate: Long?,
    onDateClick: (Long) -> Unit,
    onToggleTask: (Task, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val tasks = remember(dayDetail) { dayDetail?.tasks.orEmpty() }
    val courses = remember(dayDetail) { dayDetail?.courses.orEmpty() }
    val timedEvents = remember(dayDetail) {
        dayDetail?.events?.filter { !it.isEffectivelyAllDay && it.startTime != null }.orEmpty()
    }
    val allDayEvents = remember(dayDetail) {
        dayDetail?.events?.filter { it.isEffectivelyAllDay }.orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        // ===== 1. 日期导航栏 =====
        WeekDayBar(
            days = timeline.days,
            selectedDate = selectedDate,
            onDateClick = onDateClick,
        )

        // ===== 2. 当天的任务 / 计划 =====
        DayTodoSection(
            tasks = tasks,
            onToggleTask = onToggleTask,
            title = "当天计划",
        )

        // ===== 3. 课程安排 =====
        CourseSection(courses = courses)

        // ===== 全天事件 =====
        if (allDayEvents.isNotEmpty()) {
            AllDayEventsCard(events = allDayEvents)
        }

        // ===== 4. 时间轴 =====
        DayTimelineView(
            courses = courses,
            timedEvents = timedEvents,
            tasks = tasks,
            emptyHint = "这天还没有安排",
        )

        Spacer(Modifier.height(AppSpacing.xl))
    }
}

// =====================================================================
// 1. 日期导航栏
// =====================================================================

@Composable
private fun WeekDayBar(
    days: List<WeekDayColumn>,
    selectedDate: Long?,
    onDateClick: (Long) -> Unit,
) {
    if (days.isEmpty()) return
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                val selected = selectedDate != null && day.date == selectedDate
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(AppSpacing.sm))
                        .clickable { onDateClick(day.date) }
                        .padding(vertical = AppSpacing.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = weekDayLabel(day.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.background(MaterialTheme.colorScheme.primary)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayOfMonth(day.date),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** dayOfWeek（1 = 周一 … 7 = 周日）→ 单字 */
private fun weekDayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}

private fun dayOfMonth(dateMillis: Long): String {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate().dayOfMonth.toString()
}

// =====================================================================
// 3. 课程安排
// =====================================================================

@Composable
private fun CourseSection(courses: List<Course>) {
    if (courses.isEmpty()) return
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AppSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "课程安排",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${courses.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(AppSpacing.xxs))
        courses.sortedBy { it.startTime }.forEach { course ->
            CourseRow(course = course)
        }
    }
}

@Composable
private fun CourseRow(course: Course) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(course.color)),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    text = course.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${DateUtils.formatMinutesOfDay(course.startTime)} - " +
                DateUtils.formatMinutesOfDay(course.endTime),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// =====================================================================
// 全天事件
// =====================================================================

@Composable
private fun AllDayEventsCard(events: List<Event>) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AppSpacing.sm),
    ) {
        Text(
            text = "全天",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(AppSpacing.xxs))
        events.forEach { event ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(event.color)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
