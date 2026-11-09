package com.example.personalassistant.feature.habit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalassistant.core.ui.components.ThinProgressBar
import com.example.personalassistant.core.ui.components.ensureBarContrast
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType
import com.example.personalassistant.feature.habit.domain.model.HabitTodayItem

/**
 * Phase 26：今日习惯列表里的「一物一块」。
 *
 * ## 为什么要重画
 *
 * 原来这一行是「圆底色块 + 卡片外壳 + 选中态底色 + 两个 IconButton」，
 * 一屏 5 个习惯就是 5 张卡片 + 10 个圆按钮 —— 视觉重量全花在控件上，
 * 反而看不出「今天还差几个」。
 *
 * 新方案（见 `docs/ui-mockups/plan-d.html` 的 D6）：
 * 34dp 图标方块 + 名称 + 目标 + 右上角计数 + 2dp 进度条 + 右下角一个文字按钮。
 * 块与块之间靠一条细分隔线（`ThinDivider`）分开，不画卡片壳。
 *
 * @param item 今日的习惯项（习惯 + 今天已打卡次数）
 * @param onClick 点整块 → 选中这个习惯（统计区会切到它）
 * @param onCheckIn 打卡一次
 * @param onUndo 撤销一次
 */
@Composable
fun HabitRow(
    item: HabitTodayItem,
    onClick: () -> Unit,
    onCheckIn: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habit = item.habit
    val habitColor = Color(habit.color)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        // ===== 顶部一行：图标方块 + 名称/目标 + 计数 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(habitColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HabitIcons.resolve(habit.iconKey),
                    contentDescription = null,
                    tint = onColorFor(habitColor),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = habit.goalText,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = item.count.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = " / ${habit.targetCount}",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ===== 2dp 进度条 =====
        // 习惯色是用户自己挑的，可能很浅；进度条压在暖白轨道上，
        // 直接用原色会看不见，所以走 [ensureBarContrast] 压暗到 ≥ 3:1。
        ThinProgressBar(
            progress = item.progressFraction,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            height = 2.dp,
            progressColor = ensureBarContrast(habitColor),
        )

        // ===== 右下角文字按钮 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 「撤销」不在设计稿里，但打卡后必须能减回去 ——
            // 减到 0 就是撤销当天打卡，这是旧版一直有的能力，不能因为换皮丢掉。
            if (item.count > 0) {
                TextButton(
                    onClick = onUndo,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text(
                        text = "撤销",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.isMet) {
                Text(
                    text = "✓ 已完成",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            } else {
                TextButton(
                    onClick = onCheckIn,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text(
                        text = "打卡",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 目标副行（「每日 50 个」/「每周 3 次」）。
 *
 * 不用 `Habit.targetDescription`：它写的是「每天 50 次」，而设计稿要的是
 * 「每日 50 个」——「每日/每周」两个字更短，副行放得下；
 * 单位留空时退回「次」。
 */
private val Habit.goalText: String
    get() {
        val unit = this.unit?.takeIf { it.isNotBlank() } ?: "次"
        return when (targetType) {
            HabitTargetType.DAILY -> "每日 $targetCount $unit"
            HabitTargetType.WEEKLY -> "每周 $targetCount $unit"
        }
    }

/** 进度比例（0f..1f） */
private val HabitTodayItem.progressFraction: Float
    get() = if (habit.targetCount <= 0) 0f else (count.toFloat() / habit.targetCount).coerceIn(0f, 1f)

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
