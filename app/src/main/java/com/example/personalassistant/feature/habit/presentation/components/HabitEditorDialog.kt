package com.example.personalassistant.feature.habit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType
import com.example.personalassistant.feature.habit.presentation.HabitEditorState
import com.example.personalassistant.feature.habit.presentation.displayName

/**
 * Phase 23：新建 / 编辑习惯的弹窗
 *
 * 校验在 ViewModel 里做（[com.example.personalassistant.feature.habit.presentation.HabitViewModel.onSaveEditor]），
 * 弹窗只负责收集输入。
 *
 * Phase 26：补上**图标选择**。
 *
 * 之前 `iconKey` 是个永远等于 "star" 的死字段 —— 列表里画死了星星图标，
 * 用户既不能换也不能选。列表改成按 iconKey 解析之后，这个字段终于有意义了，
 * 弹窗里不给出选项就等于白改。
 */
@Composable
fun HabitEditorDialog(
    state: HabitEditorState,
    onChange: (HabitEditorState) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.isEditing) "编辑习惯" else "新建习惯") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onChange(state.copy(name = it)) },
                    label = { Text("习惯名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.targetCount,
                    // 只留数字：用户敲字母时直接过滤掉，比弹错误提示更顺手
                    onValueChange = { onChange(state.copy(targetCount = it.filter { ch -> ch.isDigit() })) },
                    label = { Text("目标次数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.unit,
                    onValueChange = { onChange(state.copy(unit = it)) },
                    label = { Text("单位（可选，如「杯」「页」）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HabitTargetType.entries.forEach { type ->
                        FilterChip(
                            selected = state.targetType == type,
                            onClick = { onChange(state.copy(targetType = type)) },
                            label = { Text(type.displayName()) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "图标",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                IconPickerRow(
                    selectedKey = state.iconKey,
                    color = Color(state.color),
                    onSelect = { onChange(state.copy(iconKey = it)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 图标选择行：横向滚动的图标按钮。
 *
 * 选中态画在**图标方块**上而不是给按钮铺一层色块 ——
 * 这套方案里色块是「标识」用的（分类竖条、习惯图标底），铺色块会让两者混淆。
 */
@Composable
private fun IconPickerRow(
    selectedKey: String,
    color: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HabitIcons.availableKeys.forEach { key ->
            val selected = key == selectedKey
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) color else Color.Transparent)
                    .border(
                        width = if (selected) 0.dp else 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(11.dp),
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HabitIcons.resolve(key),
                    contentDescription = key,
                    tint = if (selected) onColorFor(color) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
