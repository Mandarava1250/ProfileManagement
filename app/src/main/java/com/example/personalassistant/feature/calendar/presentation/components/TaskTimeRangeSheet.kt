package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.task.domain.model.Task
import java.time.Instant
import java.time.ZoneId

/**
 * Phase 20.1：给清单项设置时间段的底部面板
 *
 * ## 为什么放在日历里
 *
 * 清单项（tasks）的时间段是**为了在日历上占一块**才存在的 ——
 * 用户在日历里看到这条清单项，想把它挪个时间，最顺手的入口就在日历上。
 *
 * ## 语义
 *
 * - 开始 / 结束都是「当天几点几分」
 * - 结束 ≤ 开始 → **跨午夜**（比如 23:00 → 01:00，结束时间落到次日）
 * - 「清除时间段」→ 两列都置空，这条清单项回到「只有截止时间」的普通形态
 *
 * @param defaultDayStart 默认落在哪一天（一般传日历当前查看的那天）
 * @param onSave 保存（两个时间戳都已算好）
 * @param onClear 清除时间段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTimeRangeSheet(
    task: Task,
    defaultDayStart: Long,
    onSave: (startAt: Long, endAt: Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    // 已有时间段 → 打开就显示它；没有 → 默认 9:00-10:00
    val initial = remember(task.id, task.startAt, task.endAt, defaultDayStart) {
        val start = task.startAt
        val end = task.endAt
        if (start != null && end != null) {
            val startTime = Instant.ofEpochMilli(start).atZone(zone).toLocalTime()
            val endTime = Instant.ofEpochMilli(end).atZone(zone).toLocalTime()
            InitialTimeRange(startTime.hour, startTime.minute, endTime.hour, endTime.minute)
        } else {
            InitialTimeRange(9, 0, 10, 0)
        }
    }

    var startHour by remember { mutableIntStateOf(initial.startHour) }
    var startMinute by remember { mutableIntStateOf(initial.startMinute) }
    var endHour by remember { mutableIntStateOf(initial.endHour) }
    var endMinute by remember { mutableIntStateOf(initial.endMinute) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "设置时间段",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                text = "给了时间段，这条清单项就会在日历的时间轴上占一块。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ===== 开始时间 =====
            Text(text = "开始", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeDropdown(
                    label = "小时",
                    value = startHour,
                    values = (0..23).toList(),
                    onSelect = { startHour = it },
                )
                TimeDropdown(
                    label = "分钟",
                    value = startMinute,
                    values = (0..55 step 5).toList(),
                    onSelect = { startMinute = it },
                )
            }

            // ===== 结束时间 =====
            Text(text = "结束", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeDropdown(
                    label = "小时",
                    value = endHour,
                    values = (0..23).toList(),
                    onSelect = { endHour = it },
                )
                TimeDropdown(
                    label = "分钟",
                    value = endMinute,
                    values = (0..55 step 5).toList(),
                    onSelect = { endMinute = it },
                )
            }

            val crossMidnight = (endHour * 60 + endMinute) <= (startHour * 60 + startMinute)
            if (crossMidnight) {
                Text(
                    text = "结束时间落在次日 —— 会画成一条跨天条。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(AppSpacing.xs))

            // ===== 按钮 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("清除时间段")
                }
                Button(
                    onClick = {
                        val day = Instant.ofEpochMilli(defaultDayStart).atZone(zone).toLocalDate()
                        val start = day.atTime(startHour, startMinute).atZone(zone)
                        var end = day.atTime(endHour, endMinute).atZone(zone)
                        // 结束 ≤ 开始 → 跨午夜，结束时间落到次日
                        if (!end.isAfter(start)) end = end.plusDays(1)
                        onSave(
                            start.toInstant().toEpochMilli(),
                            end.toInstant().toEpochMilli(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("保存")
                }
            }
        }
    }
}

/** 打开面板时的初始时间 */
private data class InitialTimeRange(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
)

/**
 * 一个「小时 / 分钟」下拉选择器
 *
 * 用 DropdownMenu 而不是 TimePicker —— 面板里只需要两个数字，
 * 下拉比转盘更省空间，也不用处理 Material3 版本的 TimePicker 差异。
 */
@Composable
private fun TimeDropdown(
    label: String,
    value: Int,
    values: List<Int>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "%02d".format(value),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text("%02d".format(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
    Spacer(Modifier.width(4.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
