package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell

/**
 * Phase 7c：月视图网格（6×7）
 *
 * 涟宝解读：
 * - 顶部 7 个 weekday 标题（一/二/.../日）
 * - LazyVerticalGrid 6 行 × 7 列 = 42 个 cell
 * - selectedDate 高亮 + click 通过 onDateClick 回调上抛
 */
@Composable
fun MonthCalendarGrid(
    cells: List<CalendarDayCell>,
    selectedDate: Long?,
    onDateClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // 顶部周标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
            weekdays.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        // 6×7 网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(cells, key = { it.date }) { cell ->
                MonthDayCell(
                    cell = cell,
                    isSelected = cell.date == selectedDate,
                    onClick = onDateClick,
                )
            }
        }
    }
}