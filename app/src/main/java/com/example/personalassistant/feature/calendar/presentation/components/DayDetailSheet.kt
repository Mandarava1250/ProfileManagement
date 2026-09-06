package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.core.utils.DateUtils
import java.time.Instant
import java.time.ZoneId

/**
 * Phase 7c Sub-step 4：日详情 BottomSheet（月视图/周视图点击日期弹出）
 *
 * 涟宝解读：
 * - 当用户在月视图或周视图点击某个日期，弹出此 BottomSheet
 * - 内容：当日的「事件 + 任务 + 课程」聚合展示
 * - 与 DayView 不同：DayView 是 DAY 视图的主体，DayDetailSheet 是弹出的详情层
 * - 课程/任务/事件按 section 分组，每条带颜色块和元数据
 *
 * 涟宝保留接口：每个列表项都是 Composable，未来加"跳转到编辑页"只需在 onClick 回调里接 nav route。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    date: Long,
    dayDetail: DayDetail?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = remember { ZoneId.systemDefault() }
    val title = remember(date) {
        Instant.ofEpochMilli(date).atZone(zone).toLocalDate().format(
            java.time.format.DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // 顶部标题
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                dayDetail?.weekNumber?.let { week ->
                    Text(
                        text = "第 $week 周",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 内容
            if (dayDetail == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "正在加载...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!dayDetail.hasAny) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "今天没有任何安排",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 课程（按 startTime 排序）
                    if (dayDetail.courses.isNotEmpty()) {
                        item {
                            SheetSectionHeader(
                                label = "课程 (${dayDetail.courses.size})",
                                icon = Icons.Filled.School,
                            )
                        }
                        val sortedCourses = dayDetail.courses.sortedBy { it.startTime }
                        items(sortedCourses, key = { "course-${it.id}" }) { course ->
                            SheetCourseRow(course = course)
                        }
                    }

                    // 任务（按 priority + dueDate 排序）
                    if (dayDetail.tasks.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            SheetSectionHeader(
                                label = "任务 (${dayDetail.tasks.size})",
                                icon = Icons.Outlined.Assignment,
                            )
                        }
                        val sortedTasks = dayDetail.tasks
                            .sortedWith(compareByDescending<Task> { it.priority.ordinal }.thenBy { it.dueDate ?: Long.MAX_VALUE })
                        items(sortedTasks, key = { "task-${it.id}" }) { task ->
                            SheetTaskRow(task = task, zone = zone)
                        }
                    }

                    // 事件（按 isAllDay DESC, startTime ASC，Repository 已排序）
                    if (dayDetail.events.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            SheetSectionHeader(
                                label = "事件 (${dayDetail.events.size})",
                                icon = Icons.Outlined.CalendarMonth,
                            )
                        }
                        items(dayDetail.events, key = { "event-${it.id}" }) { event ->
                            SheetEventRow(event = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSectionHeader(
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
        )
    }
}

@Composable
private fun SheetEventRow(event: Event) {
    val timeText = when {
        event.isEffectivelyAllDay -> "全天"
        event.startTime != null -> {
            val endText = event.endTime?.let { DateUtils.formatTime(it) }
            if (endText != null) "${DateUtils.formatTime(event.startTime)} - $endText"
            else DateUtils.formatTime(event.startTime)
        }
        else -> ""
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(event.color)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (timeText.isNotEmpty()) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun SheetTaskRow(task: Task, zone: ZoneId) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
            task.category?.let { cat ->
                Text(
                    text = cat,
                    style = MaterialTheme.typography.labelSmall,
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

@Composable
private fun SheetCourseRow(course: Course) {
    val timeText = "${DateUtils.formatMinutesOfDay(course.startTime)} - ${DateUtils.formatMinutesOfDay(course.endTime)}"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
