package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 首屏的筛选入口按钮 —— 有生效条件时右上角带计数徽标。
 *
 * Phase 14 从 `TasksTab` 里长出来，Phase 15 清单页也用同一套：
 * 分类 / 标签 / 排序 / 分组都收进底部面板后，首屏只剩这一个入口，
 * 徽标告诉用户「现在有条件生效着」，免得以为筛选丢了。
 *
 * @param activeCount 生效条件的条数（0 = 不显示徽标）
 * @param contentDescription 无障碍描述（各页面自己给，如「筛选」）
 * @param onClick 点击回调
 */
@Composable
fun FilterEntryButton(
    activeCount: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = contentDescription,
                tint = if (activeCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (activeCount > 0) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "$activeCount",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
