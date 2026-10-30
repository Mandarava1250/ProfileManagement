package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.presentation.WeekDayColumn
import com.example.personalassistant.feature.calendar.presentation.WeekTimeline
import com.example.personalassistant.feature.task.domain.model.Task
import java.time.Instant
import java.time.ZoneId

/**
 * 周视图 = 整周 7 列并排的时间轴
 *
 * 伙伴给的草案（Demo_Week.html）长这样，从上到下：
 *
 * 1. **星期头部**：周一~周日横排，上面是「一二三四五六日」，下面是日期圆圈，选中的那天是实心色圆
 * 2. **全天标签区**：任务 / 全天日程摆成小标签，一行放不下就换行，整块最高 100dp 自己滚
 * 3. **时间轴网格**：左边一列时间刻度，右边 7 列（周一~周日）并排，每列放各自的课程和事件
 *    ——「现在」是一条红线，画在今天那一列上，左边带个小圆点
 *
 * 之前那版是「选一天看一天」，伙伴要的是整周一眼看完，所以换成了这个结构。
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
    Column(modifier = modifier.fillMaxWidth()) {
        // ===== 1. 星期头部 =====
        WeekDayHeader(
            days = timeline.days,
            selectedDate = selectedDate,
            onDateClick = onDateClick,
        )

        // ===== 2. 全天标签区 =====
        AllDayTagArea(
            tasks = dayDetail?.tasks.orEmpty(),
            events = dayDetail?.events.orEmpty(),
            onToggleTask = onToggleTask,
        )

        // ===== 3. 时间轴 7 列 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            WeekTimeGrid(days = timeline.days)
        }
    }
}

// =====================================================================
// 1. 星期头部
// =====================================================================

@Composable
private fun WeekDayHeader(
    days: List<WeekDayColumn>,
    selectedDate: Long?,
    onDateClick: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = AppSpacing.sm),
    ) {
        days.forEach { day ->
            val selected = selectedDate != null && day.date == selectedDate
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppSpacing.sm))
                    .clickable { onDateClick(day.date) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = weekDayLabel(day.dayOfWeek),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
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
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

// =====================================================================
// 2. 全天标签区
// =====================================================================

/**
 * 任务 / 全天日程摆成小标签，一行四个，超出换行，整块最高 100dp 自己滚。
 */
@Composable
private fun AllDayTagArea(
    tasks: List<Task>,
    events: List<Event>,
    onToggleTask: (Task, Boolean) -> Unit,
) {
    val allDayEvents = remember(events) { events.filter { it.isEffectivelyAllDay } }
    if (tasks.isEmpty() && allDayEvents.isEmpty()) return

    // remember 的 lambda 里不能调 MaterialTheme —— 颜色先在外面取出来
    val primary = MaterialTheme.colorScheme.primary
    val rows = remember(tasks, allDayEvents, primary) {
        val tags = tasks.map {
            TagItem(
                text = it.title,
                color = primary,
                strikethrough = it.isCompleted,
                taskId = it.id,
            )
        } + allDayEvents.map {
            TagItem(
                text = it.title,
                color = Color(it.color),
                strikethrough = false,
                taskId = null,
            )
        }
        tags.chunked(4)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 100.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { tag ->
                    TagChip(
                        text = tag.text,
                        color = tag.color,
                        strikethrough = tag.strikethrough,
                        onClick = {
                            val task = tag.taskId?.let { id -> tasks.firstOrNull { it.id == id } }
                            if (task != null) onToggleTask(task, !task.isCompleted)
                        },
                    )
                }
            }
        }
    }
}

private data class TagItem(
    val text: String,
    val color: Color,
    val strikethrough: Boolean,
    /** 任务标签才有 id —— 点一下能勾选完成 */
    val taskId: Long?,
)

@Composable
private fun TagChip(
    text: String,
    color: Color,
    strikethrough: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
        )
    }
}

// =====================================================================
// 3. 时间轴 7 列
// =====================================================================

@Composable
private fun WeekTimeGrid(days: List<WeekDayColumn>) {
    val hourHeight = 46.dp
    val gutterWidth = 42.dp
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val nowMinute = remember { currentMinuteOfDay() }
    val todayIndex = remember(days) { days.indexOfFirst { isToday(it.date) } }

    // 打开就落在「现在」附近 —— 24 小时的轴从 00:00 划到上午十点要好几屏
    LaunchedEffect(todayIndex) {
        if (todayIndex >= 0) {
            val target = with(density) {
                (nowMinute / 60f * hourHeight.toPx() - 80.dp.toPx()).coerceAtLeast(0f)
            }
            scrollState.scrollTo(target.toInt())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(hourHeight * 24),
        ) {
            // 左边：时间刻度
            Column(
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight(),
            ) {
                repeat(24) { hour ->
                    Box(
                        modifier = Modifier.height(hourHeight),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Text(
                            text = DateUtils.formatMinutesOfDay(hour * 60),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }

            // 右边：7 列
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                days.forEachIndexed { index, day ->
                    WeekDayColumnView(
                        day = day,
                        hourHeight = hourHeight,
                        showNowLine = index == todayIndex,
                        nowMinute = nowMinute,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 周一~周日里的一列。
 *
 * 列窄（360dp 屏上每列 40dp 出头），所以块里的字很小 —— 能认出是「高等数学」就行，
 * 想读全名点进日视图。
 */
@Composable
private fun WeekDayColumnView(
    day: WeekDayColumn,
    hourHeight: Dp,
    showNowLine: Boolean,
    nowMinute: Int,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(day) { day.buildWeekBlocks() }

    Box(modifier = modifier.fillMaxHeight()) {
        // 整点横线
        repeat(24) { hour ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    .align(Alignment.TopStart)
                    .offset(y = hourHeight * hour),
            )
        }

        // 课程 / 事件块
        blocks.forEach { block ->
            val top = hourHeight * (block.startMinute / 60f)
            val height = hourHeight * ((block.endMinute - block.startMinute) / 60f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp)
                    .offset(y = top)
                    .height(height - 1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(block.color.copy(alpha = 0.18f)),
            ) {
                // 左侧 3dp 语义色条
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(block.color),
                )
                Text(
                    text = block.title,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 5.dp, top = 2.dp, end = 2.dp),
                )
            }
        }

        // 「现在」红线：只画在今天那一列，左边带个小圆点
        if (showNowLine) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.error)
                    .align(Alignment.TopStart)
                    .offset(y = hourHeight * (nowMinute / 60f)),
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = hourHeight * (nowMinute / 60f) - 3.dp),
            )
        }
    }
}

/** 一列里要画哪些块：课程 + 带时刻的事件 */
private fun WeekDayColumn.buildWeekBlocks(): List<WeekBlock> {
    val courseBlocks = courses.map { course ->
        WeekBlock(
            title = course.name,
            startMinute = course.startTime,
            endMinute = course.endTime,
            color = Color(course.color),
        )
    }
    val eventBlocks = events.mapNotNull { event ->
        if (event.isEffectivelyAllDay) return@mapNotNull null
        val start = event.startTime?.toInt() ?: return@mapNotNull null
        WeekBlock(
            title = event.title,
            startMinute = start,
            // 没有结束时间就按 1 小时算 —— 零高度的块在屏幕上等于不存在
            endMinute = event.endTime?.toInt() ?: (start + 60),
            color = Color(event.color),
        )
    }
    return (courseBlocks + eventBlocks).sortedBy { it.startMinute }
}

private data class WeekBlock(
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val color: Color,
)

// =====================================================================
// 小工具
// =====================================================================

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
