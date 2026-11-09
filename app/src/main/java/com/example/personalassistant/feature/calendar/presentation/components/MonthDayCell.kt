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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 *
 * Phase 8 Sub-step 9 Step 5：右下角加农历角标
 * - 默认不显示（lunarLabel = null 时跳过）
 * - 节日：tertiary 色（与 task badge 一致，保持色系协调）
 * - 普通日期：onSurfaceVariant + alpha 0.6，避免抢数字主体
 */
@Composable
fun MonthDayCell(
    cell: CalendarDayCell,
    isSelected: Boolean,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lunarLabel: String? = null,
    isLunarFestival: Boolean = false,
) {
    val dayOfMonth = remember(cell.date) {
        Instant.ofEpochMilli(cell.date).atZone(ZoneId.systemDefault()).dayOfMonth
    }

    val textColor = when {
        cell.isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        // 非本月日期以前是 onSurfaceVariant @ 40% —— 在暖白底上只有约 2.3:1，
        // 根本读不清。现在直接用完整色值，对比度 ≥ 4.5:1。
        !cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant
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
                    // Phase 24B：习惯用中性描边色，不跟日程 / 任务 / 课程抢色
                    if (cell.hasHabits) BadgeDot(MaterialTheme.colorScheme.outline)
                }
            }
        }

        // 农历角标：右下，与 badge 区不冲突（badge 在数字下方居中，lunar 在右下）
        if (lunarLabel != null) {
            Text(
                text = lunarLabel,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = if (isLunarFestival) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    isLunarFestival -> MaterialTheme.colorScheme.tertiary
                    // 上下月占位：跟着 cell 透明度一起降一档
                    !cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
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