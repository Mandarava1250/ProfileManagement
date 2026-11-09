package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.feature.calendar.domain.model.CalendarHabitItem
import java.time.Instant
import java.time.ZoneId

/**
 * Phase 24B：补卡面板
 *
 * 月视图里点「习惯」标签行 → 打开这个面板，给**选中的那一天**补打卡。
 *
 * ## 为什么在日历侧另写一份
 *
 * 习惯页的月度打卡表（HabitMonthGrid）里的格子是 private 且不可点的，
 * 想在那一格上补卡就得改 habit 模块 —— Phase 24B 不允许动它，
 * 所以在日历侧开一个面板，避开 habit 模块的组件。
 *
 * ## count 取的是那一天
 *
 * 面板里的 [CalendarHabitItem.count] 来自那一天当天的 [DayDetail.habits]，
 * 不是「今天」—— 补卡改的就是历史那天的次数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitCheckInSheet(
    date: Long,
    habits: List<CalendarHabitItem>,
    onCheckIn: (habitId: Long, delta: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = remember { ZoneId.systemDefault() }
    val title = remember(date) {
        Instant.ofEpochMilli(date).atZone(zone).toLocalDate().format(
            java.time.format.DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "补卡",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "给这一天补上打卡记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (habits.isEmpty()) {
                Text(
                    text = "还没有习惯，先去习惯页建一个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(habits, key = { it.habitId }) { habit ->
                        CalendarHabitRow(
                            habit = habit,
                            onCheckIn = { onCheckIn(habit.habitId, 1) },
                            onUndo = { onCheckIn(habit.habitId, -1) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Phase 24B：日历里的一行习惯打卡
 *
 * ## 为什么不是直接用习惯页的 HabitRow
 *
 * 习惯页的 HabitRow 吃的是 habit 域的 HabitTodayItem（要整个 Habit 对象）。
 * 日历这边只留了轻量的 CalendarHabitItem（名字 / 颜色 / 次数 / 目标），
 * 而且 Phase 24B 不允许改 habit 模块的组件 ——
 * 所以这里按同样的版式另写一行，两边互不影响。
 */
@Composable
internal fun CalendarHabitRow(
    habit: CalendarHabitItem,
    onCheckIn: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habitColor = Color(habit.color)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(habitColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = onColorFor(habitColor),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = habit.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (habit.isMet) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = habit.progressText,
            style = MaterialTheme.typography.labelLarge,
            color = if (habit.isMet) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        // 34dp 方块 + 44dp 触达。
        //
        // 原来整个 IconButton 就是 32dp —— 低于 44dp 的触达下限，
        // 排队打卡时很容易点空。视觉块 34dp 不变（方块，不用圆形，
        // 跟清单模块的方块勾选框同一套语言），触达区撑到 44dp。
        IconButton(onClick = onCheckIn, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "打卡一次",
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        // 没打过卡时不显示撤销按钮 —— 「减一」在 0 上没有意义
        if (habit.count > 0) {
            IconButton(onClick = onUndo, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "撤销一次",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
