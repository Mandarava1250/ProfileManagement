package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.components.AppCard
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.util.TimeBlockLayout
import com.example.personalassistant.feature.calendar.presentation.TodayOverview
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.timetable.domain.model.Course
import java.time.Instant
import java.time.ZoneId

/**
 * 日视图 = 今日总览（Phase 14）
 *
 * 自上而下三段，顺序是刻意的 —— 先看要做什么，再看时间怎么排：
 *
 * 1. **预算横条**：本月预算还剩多少 + 今日零钱池（点进记账页）
 * 2. **今日待办**：来自 [DayDetail.tasks]，可直接勾选完成
 * 3. **1 小时时间轴**：课程与事件按真实时段落在时间轴上
 *
 * 与 Phase 7c 的区别：原来是「全天 / 日程 / 任务 / 课程」四段卡片流，
 * 时间块只写 `HH:mm 标题` 不占位置；现在是真正的时间轴
 * （行高固定 1 小时，块的高度就是它真实占的时长）。
 *
 * 时间块的定位算法在 [TimeBlockLayout] 里，是纯函数，可单测。
 */
@Composable
fun DayCalendarView(
    date: Long,
    dayDetail: DayDetail?,
    modifier: Modifier = Modifier,
    overview: TodayOverview? = null,
    onToggleTask: (Task, Boolean) -> Unit = { _, _ -> },
    onBudgetClick: () -> Unit = {},
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
        dayDetail?.events?.filter { !it.isEffectivelyAllDay && it.startTime != null }.orEmpty()
    }
    val tasks = remember(dayDetail) { dayDetail?.tasks.orEmpty() }
    val courses = remember(dayDetail) { dayDetail?.courses.orEmpty() }
    val weekNumber = dayDetail?.weekNumber

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ===== 标题 =====
        Column(modifier = Modifier.fillMaxWidth().padding(AppSpacing.md)) {
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

        // ===== 1. 预算横条 =====
        BudgetStrip(overview = overview, onClick = onBudgetClick)

        // ===== 2. 今日待办 =====
        TodayTodoSection(tasks = tasks, onToggleTask = onToggleTask)

        // ===== 全天事件 =====
        if (allDayEvents.isNotEmpty()) {
            AllDayRow(events = allDayEvents)
        }

        // ===== 3. 1 小时时间轴 =====
        DayTimeline(
            courses = courses,
            timedEvents = timedEvents,
        )

        Spacer(Modifier.height(AppSpacing.xl))
    }
}

// =====================================================================
// 1. 预算横条
// =====================================================================

@Composable
private fun BudgetStrip(
    overview: TodayOverview?,
    onClick: () -> Unit,
) {
    if (overview == null) return
    val appColors = LocalAppColors.current
    val remaining = overview.budgetRemaining
    val ratio = overview.usedRatio
    val pool = overview.dailyPool

    // 三段都没有内容 → 整条不显示（不占一屏的空卡片比没有更糟）
    if (remaining == null && pool == null) return

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(AppSpacing.md),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "今日零钱池",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = pool?.let { com.example.personalassistant.feature.finance.presentation.util.MoneyFormat.format(it.remainingAmount) } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (remaining != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "本月剩余",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text = com.example.personalassistant.feature.finance.presentation.util.MoneyFormat.format(remaining),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (overview.isOverBudget) appColors.expenseInk
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        if (ratio != null) {
            Spacer(Modifier.height(AppSpacing.xs))
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = if (overview.isOverBudget) appColors.expenseInk
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
        }
    }
}

// =====================================================================
// 2. 今日待办
// =====================================================================

@Composable
private fun TodayTodoSection(
    tasks: List<Task>,
    onToggleTask: (Task, Boolean) -> Unit,
) {
    if (tasks.isEmpty()) return
    val appColors = LocalAppColors.current

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AppSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Assignment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "今日待办",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${tasks.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(AppSpacing.xxs))
        tasks.forEach { task ->
            TodoRow(task = task, onToggle = { done -> onToggleTask(task, done) })
        }
    }
}

@Composable
private fun TodoRow(
    task: Task,
    onToggle: (Boolean) -> Unit,
) {
    val appColors = LocalAppColors.current
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT, TaskPriority.HIGH -> appColors.priorityHighInk
        TaskPriority.MEDIUM -> appColors.priorityMediumInk
        TaskPriority.NORMAL -> appColors.priorityLowInk
        TaskPriority.LOW -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = onToggle)
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        // 优先级不只靠颜色 —— 配一个文字标签，色觉障碍下也读得出
        Text(
            text = task.priority.label,
            style = MaterialTheme.typography.labelSmall,
            color = priorityColor,
            fontWeight = FontWeight.Medium,
        )
        task.dueDate?.let { due ->
            Spacer(Modifier.width(6.dp))
            Text(
                text = dueTimeText(due),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dueTimeText(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    return String.format(java.util.Locale.CHINA, "%02d:%02d", time.hour, time.minute)
}

// =====================================================================
// 全天事件
// =====================================================================

@Composable
private fun AllDayRow(events: List<Event>) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AppSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "全天",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// =====================================================================
// 3. 1 小时时间轴
// =====================================================================

private data class TimelineBlock(
    val key: String,
    val title: String,
    val subtitle: String?,
    val startMinute: Int,
    val endMinute: Int,
    val color: Color,
)

@Composable
private fun DayTimeline(
    courses: List<Course>,
    timedEvents: List<Event>,
) {
    val blocks = remember(courses, timedEvents) {
        val courseBlocks = courses.map { course ->
            TimelineBlock(
                key = "course-${course.id}",
                title = course.name,
                subtitle = course.location.takeIf { it.isNotBlank() },
                startMinute = course.startTime,
                endMinute = course.endTime,
                color = Color(course.color),
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
        (courseBlocks + eventBlocks).sortedBy { it.startMinute }
    }

    if (blocks.isEmpty()) {
        EmptyTimelineHint()
        return
    }

    val hourHeight = 56.dp
    val gutterWidth = 42.dp
    val columnGap = 3.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
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

@Composable
private fun TimelineBlockCard(
    block: TimelineBlock,
    timeText: String,
    modifier: Modifier = Modifier,
) {
    val foreground = onColorFor(block.color)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.chip))
            .background(block.color.copy(alpha = 0.16f))
            .border(1.dp, block.color.copy(alpha = 0.32f), RoundedCornerShape(AppRadii.chip))
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
private fun EmptyTimelineHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Text(
            text = "今天没有课程和日程",
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
