package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 7c Sub-step 4：周视图（7 列单行布局）
 *
 * 涟宝解读：
 * - 比月视图更聚焦，一屏只展示一周 7 天
 * - 顶部表头：周一~周日
 * - 每列：周几（周一/二/.../日）+ 日期数字 + 内容徽章 + 今日高亮 + 选中描边
 * - 点击日期 → onDateClick 回调（与月视图行为一致）
 *
 * 跟月视图区别：
 * - 没有上下月占位（永远 7 个 cell）
 * - 顶部表头直接绑死"一/二/三/四/五/六/日"，不会因为所在月变化
 */
@Composable
fun WeekCalendarView(
    cells: List<CalendarDayCell>,
    selectedDate: Long?,
    onDateClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekdays = remember { listOf("一", "二", "三", "四", "五", "六", "日") }
    val zone = remember { ZoneId.systemDefault() }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // 表头：周一~周日
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            weekdays.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 7 列日期行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cells.forEach { cell ->
                WeekDayCell(
                    cell = cell,
                    isSelected = cell.date == selectedDate,
                    zone = zone,
                    modifier = Modifier.weight(1f),
                    onClick = onDateClick,
                )
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    cell: CalendarDayCell,
    isSelected: Boolean,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    onClick: (Long) -> Unit,
) {
    val dayOfMonth = remember(cell.date) {
        Instant.ofEpochMilli(cell.date).atZone(zone).dayOfMonth
    }
    val isWeekend = remember(cell.date) {
        val dow = LocalDate.ofInstant(Instant.ofEpochMilli(cell.date), zone).dayOfWeek.value
        dow == 6 || dow == 7
    }

    val textColor = when {
        cell.isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        isWeekend -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .clickable { onClick(cell.date) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .let {
                        if (cell.isToday) it.background(MaterialTheme.colorScheme.primaryContainer)
                        else it
                    }
                    .let {
                        if (isSelected && !cell.isToday) {
                            it.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else if (isSelected) {
                            it.border(1.5.dp, MaterialTheme.colorScheme.onPrimaryContainer, CircleShape)
                        } else it
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                )
            }

            // 内容徽章：3 个小圆点
            if (cell.hasAny) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (cell.hasEvents) BadgeDot(MaterialTheme.colorScheme.primary)
                    if (cell.hasTasks) BadgeDot(MaterialTheme.colorScheme.tertiary)
                    if (cell.hasCourses) BadgeDot(MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun BadgeDot(color: Color) {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(color),
    )
}
