package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.util.TimeBlockLayout
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.timetable.domain.model.Course
import java.time.Instant
import java.time.ZoneId

/**
 * 1 小时行轴上的时间块
 *
 * @param key 唯一键
 * @param title 主标题
 * @param subtitle 副标题（地点 / 分类 / 优先级）
 * @param startMinute 从 00:00 起的分钟数
 * @param endMinute 结束分钟数
 * @param color 块的颜色
 * @param filled true = 实底（课程），false = 淡底（事件 / 任务）
 */
data class TimelineBlock(
    val key: String,
    val title: String,
    val subtitle: String?,
    val startMinute: Int,
    val endMinute: Int,
    val color: Color,
    val filled: Boolean = false,
)

/**
 * 1 小时行轴（Phase 14）
 *
 * 课程、事件、任务都会变成轴上的块，块的高度就是它真实占的时长。
 * 定位算法在 [TimeBlockLayout] 里，是纯函数，可单测。
 *
 * 日视图和周视图共用这一份 —— 两边的差别只是「取哪一天的数据」，
 * 画法必须一样，否则用户在两个视图之间来回切会看到不同的世界。
 *
 * @param courses 当天的课程
 * @param timedEvents 当天带时间的事件
 * @param tasks 当天的任务（带截止时间才会落到轴上）
 * @param emptyHint 空轴时的提示文案
 */
@Composable
fun DayTimelineView(
    courses: List<Course>,
    timedEvents: List<Event>,
    tasks: List<Task> = emptyList(),
    emptyHint: String = "今天没有课程和日程",
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val blocks = remember(courses, timedEvents, tasks, primaryColor) {
        buildTimelineBlocks(courses, timedEvents, tasks, primaryColor)
    }

    if (blocks.isEmpty()) {
        EmptyTimelineHint(text = emptyHint)
        return
    }

    val hourHeight = 56.dp
    val gutterWidth = 42.dp
    val columnGap = 3.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val totalWidth = maxWidth
        val ranges = blocks.map { TimeBlockLayout.TimeRange(it.startMinute, it.endMinute) }
        val placements = TimeBlockLayout.assignColumns(ranges)
        val columnCount = placements.maxOfOrNull { it.columnCount } ?: 1
        val columnWidth = (totalWidth - gutterWidth - columnGap * (columnCount - 1)) / columnCount

        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧：小时刻度
            Column(modifier = Modifier.width(gutterWidth)) {
                repeat(24) { hour ->
                    Box(modifier = Modifier.height(hourHeight), contentAlignment = Alignment.TopStart) {
                        Text(
                            text = String.format(java.util.Locale.CHINA, "%02d:00", hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            // 右侧：网格线 + 时间块
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(hourHeight * 24),
            ) {
                repeat(24) { hour ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            .align(Alignment.TopStart)
                            .offset(y = hourHeight * hour),
                    )
                }

                blocks.forEachIndexed { index, block ->
                    val span = TimeBlockLayout.toRowSpan(block.startMinute, block.endMinute)
                    val placement = placements[index]
                    TimelineBlockCard(
                        block = block,
                        timeText = "${DateUtils.formatMinutesOfDay(block.startMinute)} - " +
                            DateUtils.formatMinutesOfDay(block.endMinute),
                        modifier = Modifier
                            .offset(
                                x = (columnWidth + columnGap) * placement.columnIndex,
                                y = hourHeight * span.rowOffset,
                            )
                            .width(columnWidth)
                            .height(hourHeight * span.rowSpan - 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 把三类数据摊平成轴上的块
 *
 * 单独抽出来是为了能脱离 Compose 直接测 —— 「任务该落在几点」这种事，
 * 光看界面是验证不了的。
 */
internal fun buildTimelineBlocks(
    courses: List<Course>,
    timedEvents: List<Event>,
    tasks: List<Task>,
    primaryColor: Color,
): List<TimelineBlock> {
    val courseBlocks = courses.map { course ->
        TimelineBlock(
            key = "course-${course.id}",
            title = course.name,
            subtitle = course.location.takeIf { it.isNotBlank() },
            startMinute = course.startTime,
            endMinute = course.endTime,
            color = Color(course.color),
            filled = true,
        )
    }
    val eventBlocks = timedEvents.mapNotNull { event ->
        // Event 的时段是 Long（分钟数），TimeBlockLayout 用 Int —— 这里收窄
        val start = event.startTime?.toInt() ?: return@mapNotNull null
        TimelineBlock(
            key = "event-${event.id}",
            title = event.title,
            subtitle = event.category,
            startMinute = start,
            // 没有结束时间的事件按 1 小时算 —— 零高度的块在屏幕上等于不存在
            endMinute = event.endTime?.toInt() ?: (start + TimeBlockLayout.MINUTES_PER_ROW),
            color = Color(event.color),
        )
    }
    val taskBlocks = tasks.mapNotNull { task ->
        val due = task.dueDate ?: return@mapNotNull null
        val minute = minuteOfDay(due) ?: return@mapNotNull null
        TimelineBlock(
            key = "task-${task.id}",
            title = task.title,
            subtitle = task.priority.label,
            startMinute = minute,
            // 任务没有时长概念，统一按 1 小时占位
            endMinute = minute + TimeBlockLayout.MINUTES_PER_ROW,
            color = primaryColor,
        )
    }
    return (courseBlocks + eventBlocks + taskBlocks).sortedBy { it.startMinute }
}

/** 毫秒时间戳 → 当天第几分钟（0..1439），null = 不是当天 */
internal fun minuteOfDay(millis: Long): Int? {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    if (time.hour == 0 && time.minute == 0) {
        // 00:00 多半是「只有日期没有时刻」的默认值 —— 不占时间轴
        return null
    }
    return time.hour * 60 + time.minute
}

@Composable
private fun TimelineBlockCard(
    block: TimelineBlock,
    timeText: String,
    modifier: Modifier = Modifier,
) {
    val foreground = if (block.filled) onColorFor(block.color) else onColorFor(block.color)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.chip))
            .then(
                if (block.filled) {
                    Modifier.background(block.color.copy(alpha = 0.85f))
                } else {
                    Modifier
                        .background(block.color.copy(alpha = 0.16f))
                        .border(1.dp, block.color.copy(alpha = 0.32f), RoundedCornerShape(AppRadii.chip))
                }
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Column {
            Text(
                text = block.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = 0.75f),
                maxLines = 1,
            )
            if (block.subtitle != null) {
                Text(
                    text = block.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun EmptyTimelineHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "空着也是好事♪",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
