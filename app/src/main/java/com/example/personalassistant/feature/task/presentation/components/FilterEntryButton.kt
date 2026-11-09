package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 首屏的筛选入口按钮 —— 有生效条件时右上角带计数徽标。
 *
 * Phase 14 从 `TasksTab` 里长出来，Phase 15 清单页也用同一套：
 * 分类 / 标签 / 排序 / 分组都收进底部面板后，首屏只剩这一个入口，
 * 徽标告诉用户「现在有条件生效着」，免得以为筛选丢了。
 *
 * Phase 26：改成大标题右侧的 **44dp 圆形图标按钮**（透明底）。
 * 之前用的是 M3 的 `IconButton` + `Badge`：`Badge` 默认 `align(TopEnd)`，
 * 会直接压在图标上面，数字糊在 Tune 图标上根本看不清。
 * 这里徽标自己画，并往外挪开一点。
 */
@Composable
fun FilterEntryButton(
    activeCount: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // 44dp 圆形触达区，透明底 —— 跟大标题右侧的其它圆形按钮同一套语言
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = contentDescription,
                tint = if (activeCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }
        if (activeCount > 0) {
            // 徽标自绘：16dp 高、8dp 圆角、品牌蓝底、白字。
            // offset 让它离开图标本体（右上角往外 2dp / 往下 4dp），不压住 Tune。
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 4.dp)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$activeCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}
