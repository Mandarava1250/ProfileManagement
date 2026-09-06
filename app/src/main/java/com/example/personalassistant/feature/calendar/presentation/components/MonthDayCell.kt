package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import java.time.Instant
import java.time.ZoneId

/**
 * Phase 7c：月视图单日格子
 *
 * 涟宝解读：
 * - 上下月占位：透明度降低（onSurfaceVariant）
 * - 当日：圆形 primaryContainer 高亮
 * - 选中：圆形 primary 描边
 * - 内容 badge：3 个小圆点（event / task / course）
 */
@Composable
fun MonthDayCell(
    cell: CalendarDayCell,
    isSelected: Boolean,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayOfMonth = remember(cell.date) {
        Instant.ofEpochMilli(cell.date).atZone(ZoneId.systemDefault()).dayOfMonth
    }

    val textColor = when {
        cell.isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        !cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onClick(cell.date) }
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 当日：圆形高亮；选中：外圈描边
            Box(
                modifier = Modifier
                    .size(32.dp)
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }

            // 内容 badge
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