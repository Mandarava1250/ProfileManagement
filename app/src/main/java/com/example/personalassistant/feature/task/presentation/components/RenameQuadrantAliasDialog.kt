package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant

/**
 * 重命名象限别名对话框（Phase 8b'）
 *
 * - 校验：非空、最长 12 字符（防溢出 Tab）
 * - 保存 → 调 onConfirm 回调，UI 层负责写 DataStore
 * - 取消 / 按返回键 → 调 onDismiss
 */
@Composable
fun RenameQuadrantAliasDialog(
    quadrant: TaskQuadrant,
    currentAlias: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentAlias) }
    val focusRequester = remember { FocusRequester() }

    // 自动聚焦 + 选中全部
    LaunchedEffect(quadrant) {
        text = currentAlias
        focusRequester.requestFocus()
    }

    val isValid = text.trim().isNotEmpty() && text.length <= 12

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名「${quadrant.label}」") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = !isValid,
                supportingText = {
                    if (!isValid) {
                        Text(
                            text = if (text.isEmpty()) "别名不能为空" else "别名最长 12 字符",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        Text(
                            text = "${text.length}/12",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = isValid,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}