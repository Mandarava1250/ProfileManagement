package com.example.personalassistant.feature.task.presentation.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 提醒时间选择 BottomSheet
 *
 * - 显示内嵌 DatePicker + TimePicker（Material3 1.2+ DisplayMode.Picker 模式）
 * - 初次进入时按 [initialMillis] 设置初值；null 时取今天 + 当前小时
 * - onConfirm(millis?) 回调：null 表示清除（用户点 "不提醒"）
 * - onDismiss：仅关闭，不提交
 *
 * Phase 4a：调度后端是 FakeReminderScheduler，所以这里只是个数据采集口，不真触发。
 * Phase 5：换成 WorkManager 后即可生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePickerSheet(
    initialMillis: Long?,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = remember { ZoneId.systemDefault() }

    // 初始 LocalDateTime
    val initialDateTime: LocalDateTime = remember(initialMillis) {
        initialMillis?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime()
        } ?: run {
            val now = LocalDateTime.now(zone)
            // 默认往后挪 1 小时（避免选了过期的过去时间）
            now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        }
    }

    var dateMillis by remember(initialMillis) {
        mutableStateOf(
            initialMillis ?: initialDateTime
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        )
    }
    var hour by remember(initialMillis) { mutableStateOf(initialDateTime.hour) }
    var minute by remember(initialMillis) { mutableStateOf(initialDateTime.minute) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
    val timePickerState = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = true,
    )

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "设置提醒",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "选择日期与时间（系统会根据选择时间触发提醒）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // 内嵌日期选择
            DatePicker(state = datePickerState)
            Spacer(Modifier.height(8.dp))

            // 内嵌时间选择
            TimePicker(state = timePickerState)

            Spacer(Modifier.height(20.dp))

            // 操作行：清除 + 取消 + 确定
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { onConfirm(null) }) {
                    Text("不提醒")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Button(onClick = {
                    // selectedDateMillis 是 Long?（UTC 毫秒），先用它转 LocalDate；
                    // 若用户从未点过日期（null），用初始 dateMillis 兜底
                    val pickedDateMillis = datePickerState.selectedDateMillis ?: dateMillis
                    val pickedDate = Instant.ofEpochMilli(pickedDateMillis)
                        .atZone(zone)
                        .toLocalDate()
                    val pickedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    val finalDateTime = LocalDateTime.of(pickedDate, pickedTime)
                    val resultMillis = finalDateTime
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                    onConfirm(resultMillis)
                }) {
                    Text("确定")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
