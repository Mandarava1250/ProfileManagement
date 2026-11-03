package com.example.personalassistant.feature.habit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.habit.domain.model.HabitHeatmapWeek

/**
 * Phase 23.5：年度热力图
 *
 * 排布与 GitHub 贡献图一致：**一列一周**，从上到下是周一 → 周日，
 * 横向可滚动（53~54 列在手机上放不下）。
 *
 * 色阶：0 = 没打卡（浅灰），1~3 = 部分完成（主色逐渐加深），4 = 达成目标（主色）。
 */
@Composable
fun HabitHeatmap(
    weeks: List<HabitHeatmapWeek>,
    modifier: Modifier = Modifier,
) {
    if (weeks.isEmpty()) return
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.cells.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = heatmapColor(cell.level),
                                shape = RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
        }
    }
}

/** 热力图色阶 */
@Composable
private fun heatmapColor(level: Int) = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceVariant
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    else -> MaterialTheme.colorScheme.primary
}

/** 热力图的图例 */
@Composable
fun HabitHeatmapLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = "少",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        (0..4).forEach { level ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = heatmapColor(level),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
        Text(
            text = "多",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
