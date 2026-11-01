package com.example.personalassistant.feature.settings.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Phase 13：日历分组（智能提醒）的两个弹窗
 *
 * 从原 SettingsScreen 拆出来。
 */

/**
 * 提前通知时间选择对话框（material3 TimePicker）。
 *
 * 确认时调 viewModel.setHolidayEveningTime(hour, minute) 持久化并重新调度 Worker。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EveningTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整提前通知时间") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 节假日数据过期 → 联网同步授权弹窗（Phase 8 Sub-step 8）。
 *
 * 每年触发一次：当前年份 > 本地数据最大年份时在 init {} 期间被 ViewModel 设置 `syncPromptYear`。
 * 当前 Stub Gateway 不会真去联网，所以「立即同步」会回到 Toast「已同步」;后续 sub-step 替换为真实 API。
 */
@Composable
fun HolidaySyncPromptDialog(
    year: Int,
    maxYear: Int?,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节假日数据即将过期") },
        text = {
            Text(
                buildString {
                    append("当前年份（$year）已超过本地预置数据范围（")
                    if (maxYear != null) append(maxYear) else append("无数据")
                    append("）。是否联网同步 $year 年的节假日？")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onSyncNow) { Text("立即同步") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        },
    )
}
