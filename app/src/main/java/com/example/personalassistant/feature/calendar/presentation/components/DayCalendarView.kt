package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.timetable.domain.model.Course
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 7c Sub-step 4：日视图（24 小时时间轴 + 任务/课程聚合）
 *
 * 涟宝解读：
 * - 顶部：日期标题 + 周次提示（如果有 semester）
 * - 中间：全天事件 → 按小时分组的有时段事件 → 无时段任务 → 课程
 * - 24 小时布局简洁化：只标记关键时间点（6/9/12/14/18/22），其他时段空白
 *
 * 设计取舍：
 * - 暂时不渲染"精确时间块"（不计算事件长度），只用"HH:mm 标题"格式
 * - 任务/课程没有具体小时，按优先级/开始时间顺序排列
 */
@Composable
fun DayCalendarView(
    date: Long,
    dayDetail: DayDetail?,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val title = remember(date) {
        Instant.ofEpochMilli(date).atZone(zone).toLocalDate().format(
            java.time.format.DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA),
        )
    }

    val allDayEvents = remember(dayDetail) {
        dayDetail?.events?.filter { it.isEffectivelyAllDay }.orEmpty()
    }
    val timedEvents = remember(dayDetail) {
        dayDetail?.events?.filter { !it.isEffectivelyAllDay && it.startTime != null }
            ?.sortedBy { it.startTime }
            .orEmpty()
    }
    val tasks = remember(dayDetail) { dayDetail?.tasks.orEmpty() }
    val courses = remember(dayDetail) {
        dayDetail?.courses?.sortedBy { it.startTime }.orEmpty()
    }
    val weekNumber = dayDetail?.weekNumber

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部标题
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (weekNumber != null) {
                Text(
                    text = "第 $weekNumber 周",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (dayDetail == null) {
            EmptyDayPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 全天事件
                if (allDayEvents.isNotEmpty()) {
                    item {
                        SectionHeader(label = "全天", icon = Icons.Outlined.CalendarMonth)
                    }
                    items(allDayEvents, key = { "allday-${it.id}" }) { event ->
                        EventCard(event = event)
                    }
                }

                // 有时段事件
                if (timedEvents.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        SectionHeader(label = "日程", icon = Icons.Outlined.CalendarMonth)
                    }
                    items(timedEvents, key = { "timed-${it.id}" }) { event ->
                        TimedEventRow(event = event, zone = zone)
                    }
                }

                // 任务
                if (tasks.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        SectionHeader(label = "任务 (${tasks.size})", icon = Icons.Outlined.Assignment)
                    }
                    items(tasks, key = { "task-${it.id}" }) { task ->
                        TaskCard(task = task, zone = zone)
                    }
                }

                // 课程
                if (courses.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        SectionHeader(label = "课程 (${courses.size})", icon = Icons.Filled.School)
                    }
                    items(courses, key = { "course-${it.id}" }) { course ->
                        CourseCard(course = course)
                    }
                }

                // 空状态
                if (allDayEvents.isEmpty() && timedEvents.isEmpty()
                    && tasks.isEmpty() && courses.isEmpty()
                ) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "今天没有安排",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EventCard(event: Event) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(event.color).copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp, 24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(event.color)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            event.category?.let { cat ->
                Text(
                    text = cat,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimedEventRow(event: Event, zone: ZoneId) {
    val timeText = event.startTime?.let { DateUtils.formatTime(it) }.orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(event.color).copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(event.color),
                modifier = Modifier.width(56.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(4.dp, 24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(event.color)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (event.startTime != null && event.endTime != null) {
                    val endText = DateUtils.formatTime(event.endTime)
                    Text(
                        text = "$timeText - $endText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task, zone: ZoneId) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                task.dueDate?.let { due ->
                    val date = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    Text(
                        text = "截止 ${date.format(java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm", java.util.Locale.CHINA))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = task.priority.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun CourseCard(course: Course) {
    val startText = DateUtils.formatMinutesOfDay(course.startTime)
    val endText = DateUtils.formatMinutesOfDay(course.endTime)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(course.color).copy(alpha = 0.15f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$startText\n$endText",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(course.color),
                modifier = Modifier.width(56.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(4.dp, 32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(course.color)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (course.location.isNotBlank()) {
                    Text(
                        text = "@${course.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (course.teacher.isNotBlank()) {
                    Text(
                        text = course.teacher,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDayPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "正在加载...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
