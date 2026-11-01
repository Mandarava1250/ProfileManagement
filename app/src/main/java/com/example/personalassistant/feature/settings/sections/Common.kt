package com.example.personalassistant.feature.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 13：设置页分组的公共 UI 部件
 *
 * 设置页改成按 课表 / 任务 / 记账 / 日历 / 我的 分组后，
 * 每个分组都用同一个 [ModuleHeader] 起头，视觉上把「谁的设置」讲清楚。
 */

/**
 * 模块标题条：图标 + 模块名（+ 一句说明）。
 *
 * 用 [MaterialTheme.typography.titleLarge] 而不是 titleMedium —— 它是分组的第一层标题，
 * 下面的卡片标题才是第二层，两级要拉得开。
 */
@Composable
fun ModuleHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer8()
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (subtitle != null) {
            Spacer8()
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 8dp 间隔（沿用设置页原有写法：给 Spacer 加 padding 来撑高度） */
@Composable
fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
}

/** 时间戳 → "yyyy-MM-dd HH:mm:ss" */
fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
