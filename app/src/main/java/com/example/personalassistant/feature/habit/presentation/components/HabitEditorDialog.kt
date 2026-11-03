package com.example.personalassistant.feature.habit.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType
import com.example.personalassistant.feature.habit.presentation.HabitEditorState
import com.example.personalassistant.feature.habit.presentation.displayName

/**
 * Phase 23：新建 / 编辑习惯的弹窗
 *
 * 校验在 ViewModel 里做（[com.example.personalassistant.feature.habit.presentation.HabitViewModel.onSaveEditor]），
 * 弹窗只负责收集输入。
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
