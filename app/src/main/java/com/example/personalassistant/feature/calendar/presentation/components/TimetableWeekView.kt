package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.util.TimeBlockLayout
import com.example.personalassistant.feature.calendar.presentation.WeekDayColumn
import com.example.personalassistant.feature.calendar.presentation.WeekTimeline
import java.time.Instant
import java.time.ZoneId

/**
 * 周视图 = 课表（Phase 14）
 *
 * 结构与课表 App 一致：**固定 1 小时行轴** × 7 列（周一 ~ 周日）。
 *
 * - 纵向：24 行，每行 1 小时，行高固定 —— 所以 8 点的课和 20 点的课在屏幕上
 *   的垂直距离永远成正比，不会因为某一天事情多就挤成一团。
 * - 横向：7 列放不下（每列至少要 72dp 才放得下课程名），所以右侧可横向滚动，
 *   左侧的时间刻度固定不动。
 * - 同一天里课程和事件撞了 → 左右分栏（[TimeBlockLayout.assignColumns]），
 *   不会互相盖住。
 */
@Composable
fun TimetableWeekView(
    timeline: WeekTimeline,
    modifier: Modifier = Modifier,
    selectedDate: Long? = null,
    onDateClick: (Long) -> Unit = {},
) {
    val hourHeight = 46.dp
    val gutterWidth = 40.dp
    val columnWidth = 72.dp

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(verticalScroll),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // ===== 左侧：固定的时间刻度 =====
            Column(modifier = Modifier.width(gutterWidth)) {
                // 表头占位（与右侧日期行等高）
                Spacer(Modifier.height(WeekHeaderHeight))
                repeat(24) { hour ->
                    Box(modifier = Modifier.height(hourHeight), contentAlignment = Alignment.TopStart) {
                        Text(
                            text = String.format(java.util.Locale.CHINA, "%02d", hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // ===== 右侧：7 列（可横向滚动）=====
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScroll),
            ) {
                timeline.days.forEach { day ->
                    DayColumn(
                        day = day,
                        hourHeight = hourHeight,
                        modifier = Modifier.width(columnWidth),
                        isSelected = selectedDate != null && selectedDate == day.date,
                        onDateClick = { onDateClick(day.date) },
                    )
                }
            }
        }
        Spacer(Modifier.height(AppRadii.hero))
    }
}

/** 表头（日期行）的高度 —— 左侧刻度用它占位，两边对齐 */
private val WeekHeaderHeight = 44.dp

@Composable
private fun DayColumn(
    day: WeekDayColumn,
    hourHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onDateClick: () -> Unit = {},
) {
    val zone = remember { ZoneId.systemDefault() }
    val dayOfMonth = remember(day.date) {
        Instant.ofEpochMilli(day.date).atZone(zone).dayOfMonth
    }
    val weekLabel = remember(day.dayOfWeek) {
        WEEK_LABELS.getOrElse(day.dayOfWeek - 1) { "" }
    }

    // 这一列里的块：课程 + 有时段的事件
    val blocks = remember(day) {
        val courseBlocks = day.courses.map { course ->
            WeekBlock(
                key = "course-${course.id}",
                title = course.name,
                subtitle = course.location.takeIf { it.isNotBlank() },
                startMinute = course.startTime,
                endMinute = course.endTime,
                color = Color(course.color),
                isCourse = true,
            )
        }
        val eventBlocks = day.events.mapNotNull { event ->
            if (event.isEffectivelyAllDay) return@mapNotNull null
            val start = event.startTime?.toInt() ?: return@mapNotNull null
            WeekBlock(
                key = "event-${event.id}",
                title = event.title,
                subtitle = event.category,
                startMinute = start,
                endMinute = event.endTime?.toInt() ?: (start + TimeBlockLayout.MINUTES_PER_ROW),
                color = Color(event.color),
                isCourse = false,
            )
        }
        (courseBlocks + eventBlocks).sortedBy { it.startMinute }
    }

    val ranges = remember(blocks) {
        blocks.map { TimeBlockLayout.TimeRange(it.startMinute, it.endMinute) }
    }
    val placements = remember(ranges) { TimeBlockLayout.assignColumns(ranges) }
    val columnCount = placements.maxOfOrNull { it.columnCount } ?: 1

    Column(modifier = modifier) {
        // ===== 表头 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WeekHeaderHeight)
                .then(
                    if (isSelected) {
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onDateClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = weekLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // ===== 网格 + 块 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hourHeight * 24),
        ) {
            // 竖线（列分隔）
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            )
            // 横线（小时分隔）
            repeat(24) { hour ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        .align(Alignment.TopStart)
                        .offset(y = hourHeight * hour),
                )
            }

            blocks.forEachIndexed { index, block ->
                val span = TimeBlockLayout.toRowSpan(block.startMinute, block.endMinute)
                val placement = placements[index]
                val blockWidth = (72.dp - 4.dp) / placement.columnCount
                WeekBlockCard(
                    block = block,
                    timeText = "${DateUtils.formatMinutesOfDay(block.startMinute)}-" +
                        DateUtils.formatMinutesOfDay(block.endMinute),
                    modifier = Modifier
                        .offset(
                            x = (blockWidth + 2.dp) * placement.columnIndex + 2.dp,
                            y = hourHeight * span.rowOffset + 1.dp,
                        )
                        .width(blockWidth)
                        .height(hourHeight * span.rowSpan - 2.dp),
                )
            }
        }
    }
}

private data class WeekBlock(
    val key: String,
    val title: String,
    val subtitle: String?,
    val startMinute: Int,
    val endMinute: Int,
    val color: Color,
    val isCourse: Boolean,
)

@Composable
private fun WeekBlockCard(
    block: WeekBlock,
    timeText: String,
    modifier: Modifier = Modifier,
) {
    // 课程块是实底色（用户自己挑的浅色），事件块是淡底 —— 两者一眼能分清
    val container = if (block.isCourse) block.color else block.color.copy(alpha = 0.14f)
    val foreground = if (block.isCourse) onColorFor(block.color) else block.color

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.chip))
            .background(container)
            .then(
                if (block.isCourse) {
                    Modifier.border(
                        1.dp,
                        block.color.copy(alpha = 0.5f),
                        RoundedCornerShape(AppRadii.chip),
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Column {
            Text(
                text = block.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

private val WEEK_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
