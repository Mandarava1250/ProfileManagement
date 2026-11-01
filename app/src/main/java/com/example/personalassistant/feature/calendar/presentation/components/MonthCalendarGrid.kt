package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import com.example.personalassistant.feature.calendar.presentation.CalendarUiState

/** 顶部周标题（周一 ~ 周日）—— 固定内容，提成文件级常量，别每次重组都新建 List */
private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * Phase 7c：月视图网格（6×7）
 *
 * 涟宝解读：
 * - 顶部 7 个 weekday 标题（一/二/.../日）
 * - LazyVerticalGrid 6 行 × 7 列 = 42 个 cell
 * - selectedDate 高亮 + click 通过 onDateClick 回调上抛
 *
 * Phase 8 Sub-step 9 Step 5：每个 cell 携带农历角标
 * - [lunarLabels] 的 key = [CalendarDayCell.date]（当天 00:00 millis）
 * - 找不到时不渲染（兼容旧 caller / 测试）
 */
@Composable
fun MonthCalendarGrid(
    cells: List<CalendarDayCell>,
    selectedDate: Long?,
    onDateClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lunarLabels: Map<Long, CalendarUiState.LunarCellLabel> = emptyMap(),
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // 顶部周标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            WEEKDAYS.forEach { day ->
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
        //
        // Phase 14 修崩溃：LazyVerticalGrid 不能放进 Column(Modifier.verticalScroll()) ——
        // 外层给了无限高度约束，lazy 容器直接抛 IllegalStateException，App 直接崩。
        // 现在外层给它一份有界高度（weight(1f) 分配的剩余空间），网格自己在里面滚。
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        ) {
            items(cells, key = { it.date }) { cell ->
                val label = lunarLabels[cell.date]
                MonthDayCell(
                    cell = cell,
                    isSelected = cell.date == selectedDate,
                    onClick = onDateClick,
                    lunarLabel = label?.text,
                    isLunarFestival = label?.isFestival == true,
                )
            }
        }
    }
}