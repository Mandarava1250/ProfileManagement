package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant

/**
 * 任务象限区块头
 *
 * 显示一个色点 + 象限名 + (数量)，用于分隔四象限 + 未排期。
 */
@Composable
fun QuadrantSection(
    quadrant: TaskQuadrant,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val color = when (quadrant) {
        TaskQuadrant.P1 -> MaterialTheme.colorScheme.error          // 🔴 红
        TaskQuadrant.P2 -> MaterialTheme.colorScheme.tertiary      // 🟡 黄
        TaskQuadrant.P3 -> MaterialTheme.colorScheme.primary       // 🟢 蓝/绿（用 primary 表达"可执行"）
        TaskQuadrant.P4 -> MaterialTheme.colorScheme.outline       // ⚪ 灰
        TaskQuadrant.UNSCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = quadrant.label,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
