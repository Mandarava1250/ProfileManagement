package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.CompletionStats

/**
 * Phase 22.6：完成率卡片
 *
 * 显示三个数：**完成率**（近 7 天）、**拖延**（逾期未完成）、**连续**（连续完成天数）。
 *
 * 刻意不画折线图 —— 方案里写的是 `MPAndroidChart`，但为此引一个新依赖不划算：
 * 三个数字已经把「这周做得怎么样」说清楚了，而且**不占地方**（清单页上方一条即可）。
 */
@Composable
fun CompletionStatsCard(
    stats: CompletionStats,
    modifier: Modifier = Modifier,
) {
    if (stats.isEmpty) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "近 7 天完成率",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${stats.completionPercent}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 进度条：一眼看出完成率的量级
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(stats.completionRate.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip(
                label = "已完成",
                value = "${stats.completed}/${stats.totalPlanned}",
            )
            if (stats.overdue > 0) {
                StatChip(
                    label = "拖延",
                    value = stats.overdue.toString(),
                    valueColor = MaterialTheme.colorScheme.error,
                )
            }
            if (stats.streakDays > 0) {
                StatChip(
                    label = "连续",
                    value = "${stats.streakDays} 天",
                )
            }
        }
    }
}

/** 完成率卡片里的一小块统计 */
@Composable
private fun StatChip(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "$label ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}
