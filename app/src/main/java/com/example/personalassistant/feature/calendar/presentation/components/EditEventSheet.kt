package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.calendar.presentation.EventEditState
import com.example.personalassistant.core.utils.DateUtils
import java.time.Instant
import java.time.ZoneId

/**
 * Phase 7c Sub-step 5：事件编辑 BottomSheet（新建 / 编辑复用）
 *
 * ## 接口（Stateless）
 * - 父组件持有 [EventEditState] + 派发修改回调
 * - 父组件负责判断「保存」按钮是否禁用（这里只调用 onSave）
 * - 时间字段：hasTime=false → 隐藏时段；hasTime=true → 显示 hh:mm 输入框
 *
 * ## 字段
 * - 标题（必填）
 * - 日期显示（read-only，编辑日期通过主日历导航，不在此 Sheet 内改）
 * - 全天开关：true → 隐藏开始/结束时间
 * - 有时段开关：true → 显示开始/结束时间
 * - 分类 / 备注 / 提醒分钟数
 * - 删除按钮（仅 originalId > 0 显示）
 *
 * 设计取舍：
 * - 表单字段用本地 mutableStateOf 而不是直接 EventEditState.copy（避免每次按键触发整个 UIState 重组）
 * - onTitleChange / onNoteChange 父组件调用时手动 copy 一次
 * - 时间字段用两个 TextField 拼"HH:mm"格式，不引 DatePickerDialog（保持轻量）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventSheet(
    state: EventEditState,
    onTitleChange: (String) -> Unit,
    onToggleAllDay: (Boolean) -> Unit,
    onToggleHasTime: (Boolean) -> Unit,
    onStartTimeChange: (hour: Int, minute: Int) -> Unit,
    onEndTimeChange: (hour: Int, minute: Int) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onReminderChange: (Int?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val dateText = remember(state.date) {
        Instant.ofEpochMilli(state.date).atZone(zone).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 EEEE", java.util.Locale.CHINA))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶部标题
            Text(
                text = if (state.isNew) "新建事件" else "编辑事件",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // ===== 标题 =====
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("标题") },
                singleLine = true,
                isError = state.title.isBlank(),
                supportingText = if (state.title.isBlank()) {
                    { Text("标题不能为空") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // ===== 全天开关 =====
            ToggleRow(
                label = "全天",
                checked = state.isAllDay,
                onCheckedChange = onToggleAllDay,
            )

            // ===== 有时段开关 =====
            ToggleRow(
                label = "指定时段",
                checked = state.hasTime,
                onCheckedChange = onToggleHasTime,
            )

            // ===== 时段选择（hasTime=true 时显示）=====
            if (state.hasTime) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TimeField(
                        label = "开始",
                        hour = state.startHour,
                        minute = state.startMinute,
                        onChange = onStartTimeChange,
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        label = "结束",
                        hour = state.endHour,
                        minute = state.endMinute,
                        onChange = onEndTimeChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ===== 分类 =====
            OutlinedTextField(
                value = state.category,
                onValueChange = onCategoryChange,
                label = { Text("分类（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // ===== 备注 =====
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text("备注（可选）") },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // ===== 提醒 =====
            ReminderRow(
                reminderMinutes = state.reminderMinutes,
                onChange = onReminderChange,
            )

            Spacer(Modifier.height(20.dp))

            // ===== 操作按钮 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.padding(end = 4.dp))
                        Text("删除")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Button(
                    onClick = onSave,
                    enabled = state.title.isNotBlank(),
                ) {
                    Text("保存")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * "HH:mm" 格式时间输入（两个 Int 字段）
 *
 * 涟宝没用 TimePickerDialog（保持简单）。
 * 解析规则：
 * - 输入必须是 "HH:mm"（2 位 hour + 2 位 minute）
 * - 解析失败时本次输入被忽略
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    hour: Int?,
    minute: Int?,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(hour, minute) {
        mutableStateOf(
            if (hour != null && minute != null) {
                "%02d:%02d".format(hour, minute)
            } else ""
        )
    }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            if (newText.length == 5 && newText[2] == ':') {
                val h = newText.substring(0, 2).toIntOrNull()
                val m = newText.substring(3, 5).toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) {
                    onChange(h, m)
                }
            }
        },
        label = { Text(label) },
        singleLine = true,
        placeholder = { Text("HH:mm") },
        modifier = modifier,
    )
}

/**
 * 提醒分钟数：null = 不提醒；常见值 0/5/10/15/30/60
 */
@Composable
private fun ReminderRow(
    reminderMinutes: Int?,
    onChange: (Int?) -> Unit,
) {
    val options = remember { listOf<Pair<String, Int?>>(
        "无" to null,
        "准时" to 0,
        "5 分钟" to 5,
        "15 分钟" to 15,
        "30 分钟" to 30,
        "1 小时" to 60,
    ) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("提醒", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (label, value) ->
                val selected = reminderMinutes == value
                if (selected) {
                    Button(
                        onClick = { onChange(value) },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) { Text(label, maxLines = 1) }
                } else {
                    OutlinedButton(
                        onClick = { onChange(value) },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
    }
}
