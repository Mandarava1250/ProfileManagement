package com.example.personalassistant.feature.habit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.feature.habit.domain.model.HabitTodayItem

/**
 * Phase 23：今日习惯列表的一行
 *
 * - 点名字 → 选中它看统计
 * - 点「+」→ 打卡一次（count + 1）
 * - 点「−」→ 撤销一次（count - 1，减到 0 即取消当天打卡）
 */
@Composable
fun HabitRow(
    item: HabitTodayItem,
    selected: Boolean,
    onClick: () -> Unit,
    onCheckIn: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habitColor = Color(item.habit.color)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(habitColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = onColorFor(habitColor),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(AppSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isMet) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = item.habit.targetDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = item.progressText,
                style = MaterialTheme.typography.labelLarge,
                color = if (item.isMet) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            IconButton(onClick = onCheckIn) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "打卡一次",
                )
            }
            // 没打过卡时不显示撤销按钮 —— 「减一」在 0 上没有意义
            if (item.count > 0) {
                IconButton(onClick = onUndo) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "撤销一次",
                    )
                }
            }
        }
    }
}

/**
 * Phase 23.3：清单抽屉里的「习惯」入口
 *
 * ## 为什么单独放在 habit 模块
 *
 * 抽屉本体在 `feature/task`（Phase 17 的 ChecklistDrawer），而 Phase 23 不允许改 task 模块 ——
 * 所以这里把入口做成**可直接嵌入的独立组件**，主代理在抽屉里加一行
 * `HabitDrawerEntry(onClick = { ... })` 就能接上，不用把习惯的 UI 搬进 task 模块。
 *
 * 视觉上与抽屉里的其它行保持一致（图标 + 文字 + 透明底 + 点击高亮）。
 */
@Composable
fun HabitDrawerEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "习惯",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
