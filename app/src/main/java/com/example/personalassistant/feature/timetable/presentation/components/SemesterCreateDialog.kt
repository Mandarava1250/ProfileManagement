package com.example.personalassistant.feature.timetable.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.utils.DateUtils

/**
 * 学期创建对话框（弹窗）
 *
 * 字段：name（必填）+ startDate / endDate（必填，格式 yyyy-MM-dd）
 *
 * 设计取舍：
 *  - 暂不用 DatePickerDialog：避免再引一组 Material3 state + density 兼容代码；
 *    给 YAGNI 留个清爽入口，真要切 DatePicker 再升级。
 *  - startDate 默认 = 本周一；endDate 默认 = 本周一 + 18 周（半个学年）。
 *  - 校验全部走 [DateUtils.parseDate] 失败 → formError 走对话框底部文案，
 *    不弹 Snackbar，避免弹窗开着又叠一个提示让用户晕。
 */
@Composable
fun SemesterCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, startDate: Long, endDate: Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf(DateUtils.formatDate(DateUtils.weekStartMillis())) }
    var endText by remember {
        // 默认 endDate = 本周一 + 18 周（半个学年）
        val millis = DateUtils.weekStartMillis() + 18L * 7L * 24L * 60L * 60L * 1000L
        mutableStateOf(DateUtils.formatDate(millis))
    }
    var formError by remember { mutableStateOf<String?>(null) }

    // 提交逻辑抽出来：让"创建"按钮和软键盘"完成"都走同一条校验路径，
    // 校验失败时直接更新 formError 由 Composable 重绘展示。
    // 用 if/else 链而非 return —— val submit: () -> Unit = {…} 是 lambda 赋值，
    // 标签式 `return@submit` 在这种语法下 Kotlin 编译器不允许。
    val submit: () -> Unit = {
        val trimmed = name.trim()
        val start = DateUtils.parseDate(startText)
        val end = DateUtils.parseDate(endText)
        formError = when {
            trimmed.isEmpty() -> "学期名称不能为空"
            start == null -> "开始日期格式不正确（示例：2025-09-01）"
            end == null -> "结束日期格式不正确（示例：2026-01-15）"
            end < start -> "结束日期不能早于开始日期"
            else -> null
        }
        if (formError == null) {
            onConfirm(trimmed, start!!, end!!)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建学期") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; formError = null },
                    label = { Text("学期名称（如 2024-2025-1）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it; formError = null },
                    label = { Text("开始日期（yyyy-MM-dd）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it; formError = null },
                    label = { Text("结束日期（yyyy-MM-dd）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                formError?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 编辑已有学期对话框：name / startDate / endDate
 *
 * 与 [SemesterCreateDialog] 共享校验 / IME Action / formError 文案样式。
 * 区别：字段初值用 [initialName] / [initialStart] / [initialEnd]，回调用
 * (oldName, newName, newStart, newEnd) 让调用方知道"原名 → 新名"，便于
 * 在重命名时同步 Course.semester 引用。
 */
@Composable
fun SemesterEditDialog(
    initialName: String,
    initialStart: Long,
    initialEnd: Long,
    onDismiss: () -> Unit,
    onConfirm: (oldName: String, newName: String, newStart: Long, newEnd: Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var startText by remember { mutableStateOf(DateUtils.formatDate(initialStart)) }
    var endText by remember { mutableStateOf(DateUtils.formatDate(initialEnd)) }
    var formError by remember { mutableStateOf<String?>(null) }

    val submit: () -> Unit = {
        val trimmed = name.trim()
        val start = DateUtils.parseDate(startText)
        val end = DateUtils.parseDate(endText)
        formError = when {
            trimmed.isEmpty() -> "学期名称不能为空"
            start == null -> "开始日期格式不正确（示例：2025-09-01）"
            end == null -> "结束日期格式不正确（示例：2026-01-15）"
            end < start -> "结束日期不能早于开始日期"
            else -> null
        }
        if (formError == null) {
            onConfirm(initialName, trimmed, start!!, end!!)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑学期") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; formError = null },
                    label = { Text("学期名称") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it; formError = null },
                    label = { Text("开始日期（yyyy-MM-dd）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it; formError = null },
                    label = { Text("结束日期（yyyy-MM-dd）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                formError?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
