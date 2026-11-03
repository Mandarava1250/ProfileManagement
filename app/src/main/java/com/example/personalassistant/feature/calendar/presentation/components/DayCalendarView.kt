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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.calendar.domain.model.CalendarConflictEntry
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.presentation.TodayOverview
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import java.time.Instant
import java.time.ZoneId

/**
 * 日视图 = 今日总览（Phase 14）
 *
 * 自上而下三段，顺序是刻意的 —— 先看要做什么，再看时间怎么排：
 *
 * 1. **预算横条**：本月预算还剩多少 + 今日零钱池（点进记账页）
 * 2. **今日待办**：来自 [DayDetail.tasks]，可直接勾选完成
 * 3. **1 小时时间轴**：课程、事件、任务按真实时段落在时间轴上
 *
 * 与 Phase 7c 的区别：原来是「全天 / 日程 / 任务 / 课程」四段卡片流，
 * 时间块只写 `HH:mm 标题` 不占位置；现在是真正的时间轴
 * （行高固定 1 小时，块的高度就是它真实占的时长）。
 *
 * 时间轴本体在 [DayTimelineView] —— 日视图和周视图共用同一份画法。
 */
@Composable
fun DayCalendarView(
    date: Long,
    dayDetail: DayDetail?,
    modifier: Modifier = Modifier,
    overview: TodayOverview? = null,
    onToggleTask: (Task, Boolean) -> Unit = { _, _ -> },
    onBudgetClick: () -> Unit = {},
    // Phase 20.6：点标题跳清单项详情
    onTaskClick: (Task) -> Unit = {},
    // Phase 20.1：点时钟图标设置清单项的时间段
    onTaskTimeRangeClick: (Task) -> Unit = {},
    // Phase 20.5：清单项 ↔ 课程 的冲突
    conflicts: List<CalendarConflictEntry> = emptyList(),
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
        // Phase 15 P1-2.1：模块之间留白 —— 预算横条 / 今日待办 / 时间轴三段
        // 挤在一起会糊成一片，分不清哪段是哪段。
        // 留白加大到 md，并在段与段之间加一条分隔线。
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        // ===== 标题 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md)
                .padding(top = AppSpacing.sm),
        ) {
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

        // Phase 15 P1-2.1：分段线 —— 光靠留白还不够明显
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ===== 2. 今日待办 =====
        DayTodoSection(
            tasks = tasks,
            onToggleTask = onToggleTask,
            onTaskClick = onTaskClick,
            onTaskTimeRangeClick = onTaskTimeRangeClick,
        )

        // Phase 20.5：清单项与课程撞车了 —— 复用事件编辑器那套冲突横幅
        conflicts.forEach { conflict ->
            ConflictWarningBanner(info = conflict.toEventConflictInfo())
        }

        // ===== 全天事件 =====
        if (allDayEvents.isNotEmpty()) {
            AllDayRow(events = allDayEvents)
        }

        // Phase 15 P1-2.1：时间轴之前也来一条线
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ===== 3. 1 小时时间轴 =====
        DayTimelineView(
            courses = courses,
            timedEvents = timedEvents,
            tasks = tasks,
            emptyHint = "今天没有课程和日程",
            // Phase 14：今天是今天就画「现在」红线，一眼知道现在几点
            showNowLine = isToday(date),
            // Phase 20.4 / 20.7：带上当天的时间上下文 ——
            // 带时段的清单项按真实时长占位，跨午夜的事件不截断
            dayStart = date,
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

/**
 * 待办列表（日视图与周视图共用）
 */
@Composable
internal fun DayTodoSection(
    tasks: List<Task>,
    onToggleTask: (Task, Boolean) -> Unit,
    title: String = "今日待办",
    onTaskClick: (Task) -> Unit = {},
    onTaskTimeRangeClick: (Task) -> Unit = {},
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
                text = title,
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
            DayTodoRow(
                task = task,
                onToggle = { done -> onToggleTask(task, done) },
                onClick = { onTaskClick(task) },
                onTimeRangeClick = { onTaskTimeRangeClick(task) },
            )
        }
    }
}

@Composable
internal fun DayTodoRow(
    task: Task,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit = {},
    onTimeRangeClick: () -> Unit = {},
) {
    val appColors = LocalAppColors.current
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT, TaskPriority.HIGH -> appColors.priorityHighInk
        TaskPriority.MEDIUM -> appColors.priorityMediumInk
        TaskPriority.NORMAL -> appColors.priorityLowInk
        TaskPriority.LOW -> MaterialTheme.colorScheme.outline
    }
    // Phase 20.1：带时间段的清单项显示它自己的时段（没有才显示截止时刻）
    val rangeText = remember(task.startAt, task.endAt) { taskTimeRangeText(task) }

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
            // Phase 20.6：点标题跳清单项详情
            modifier = Modifier
                .weight(1f)
                .clickable { onClick() },
        )
        Spacer(Modifier.width(6.dp))
        // 优先级不只靠颜色 —— 配一个文字标签，色觉障碍下也读得出
        Text(
            text = task.priority.label,
            style = MaterialTheme.typography.labelSmall,
            color = priorityColor,
            fontWeight = FontWeight.Medium,
        )
        val timeText = rangeText ?: task.dueDate?.let { due ->
            // 「时:分」串缓存：dueDate 与时区都没变时，不必每次重组都重算
            // （zone 也进 key —— 时区变了要跟着重算，保证与原来每次重算完全一致）
            remember(due, ZoneId.systemDefault()) { dueTimeText(due) }
        }
        if (timeText != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Phase 20.1：设置时间段（给了时段就显示「有时段」的样子）
        IconButton(
            onClick = onTimeRangeClick,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = "设置时间段",
                tint = if (rangeText != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Phase 20.1：清单项的时间段文案（HH:mm-HH:mm），没填时段 → null
 */
private fun taskTimeRangeText(task: Task): String? {
    val start = task.startAt ?: return null
    val end = task.endAt ?: return null
    return "${dueTimeText(start)}-${dueTimeText(end)}"
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
