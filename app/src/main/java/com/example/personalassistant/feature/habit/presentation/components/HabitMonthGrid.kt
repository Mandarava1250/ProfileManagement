package com.example.personalassistant.feature.habit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.habit.domain.model.HabitDayCell
import com.example.personalassistant.feature.habit.domain.util.HabitDates

/**
 * Phase 23.5：月度打卡表
 *
 * 一行 7 格（周一 → 周日），没打卡的日子也占一格（浅色）——
 * 用户要能一眼看出「哪天漏了」，只画有记录的日子就失去意义了。
 */
@Composable
fun HabitMonthGrid(
    cells: List<HabitDayCell>,
    modifier: Modifier = Modifier,
) {
    if (cells.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        // 星期表头
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEK_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    MonthCell(cell = cell, modifier = Modifier.weight(1f))
                }
                // 补齐最后一周的空格子，保证每行都是 7 列
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 月度表的一格 */
@Composable
private fun MonthCell(
    cell: HabitDayCell,
    modifier: Modifier = Modifier,
) {
    val day = HabitDates.toLocalDate(cell.date).dayOfMonth
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(
                color = if (cell.isMet) {
                    MaterialTheme.colorScheme.primary
                } else if (cell.count > 0) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (cell.isMet) FontWeight.Bold else FontWeight.Normal,
            color = if (cell.isMet) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 星期一开头的星期表头 */
private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 月度表与热力图之间的间距 */
internal val HABIT_GRID_SPACING = AppSpacing.xxs
