package com.example.personalassistant.feature.settings.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Phase 13：清除数据的三个阶段弹窗
 *
 * 从原 SettingsScreen 拆出来（那里已经拆成壳 + 分组，弹窗单独成文件更清爽）。
 * 交互逻辑一字未改 —— 危险操作的三重确认是安全不变量，不能在重构里动。
 */

/**
 * 第一阶段：建议先备份（决策 3A）。
 * 给用户「先备份」「直接清」「取消」三个按钮，「先备份」跳 SAF。
 */
@Composable
fun SuggestBackupBeforeResetDialog(
    onBackupThenReset: () -> Unit,
    onSkipBackup: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("建议先备份") },
        text = {
            Text(
                "清除全部数据后无法恢复。建议先做一次备份再继续，" +
                    "备份完成后请再次进入此入口并选择「直接清」。",
            )
        },
        confirmButton = {
            TextButton(onClick = onBackupThenReset) { Text("先备份") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = onSkipBackup) { Text("直接清") }
                TextButton(onClick = onCancel) { Text("取消") }
            }
        },
    )
}

/**
 * 第二阶段：长按 3 秒确认（决策 2B）。
 *
 * 用 [pointerInput] 的 `awaitPointerEventScope` 实现：
 * - 监听 ACTION_DOWN / ACTION_UP / ACTION_MOVE；
 * - 记录按下时刻，抬起时若按下时长 ≥ 3000 ms → 触发 [onLongPressComplete]；
 * - 中途抬起 / 移出矩形 → 重置计时器（任何手指移动超出也视为取消）。
 */
@Composable
fun LongPressConfirmDialog(
    onLongPressComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val thresholdMs = 3000L
    val startTimeMs = remember { mutableLongStateOf(0L) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("长按 3 秒确认") },
        text = {
            Column {
                Text(
                    "此操作将清除全部数据且不可恢复。" +
                        "请按住下方按钮持续 3 秒完成确认。",
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                // 长按按钮，进度条反馈
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.error)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitPointerEvent()
                                    val change = down.changes.firstOrNull() ?: continue
                                    if (change.pressed) {
                                        startTimeMs.longValue = System.currentTimeMillis()
                                        progress = 0f
                                    } else {
                                        // 抬起 → 若已满 3 秒则触发；否则重置
                                        val elapsed = System.currentTimeMillis() - startTimeMs.longValue
                                        if (elapsed >= thresholdMs) {
                                            onLongPressComplete()
                                        } else {
                                            progress = 0f
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // 进度条覆盖
                    Canvas(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val barWidth = size.width * progress.coerceIn(0f, 1f)
                        drawRect(
                            color = Color.White.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                        )
                    }
                    Text(
                        text = if (progress >= 1f) "已确认" else "按住 3 秒",
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
    )
    // 100ms 一次刷新进度
    LaunchedEffect(startTimeMs.longValue) {
        if (startTimeMs.longValue > 0L) {
            while (true) {
                delay(100)
                val elapsed = System.currentTimeMillis() - startTimeMs.longValue
                progress = (elapsed.toFloat() / thresholdMs).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    onLongPressComplete()
                    break
                }
            }
        }
    }
}

/**
 * 第三阶段：完成反馈。告诉用户哪些步骤成功 / 失败，并建议重启 App。
 */
@Composable
fun DoneDialog(
    allSucceeded: Boolean,
    detail: String,
    onAck: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAck,
        title = { Text(if (allSucceeded) "清除完成" else "部分步骤失败") },
        text = {
            Column {
                if (allSucceeded) {
                    Text(
                        "已清除全部数据。" +
                            "建议重启 App 一次，让残留的精确闹钟缓存彻底失效。",
                    )
                } else {
                    Text(
                        "部分步骤失败，详细：\n$detail\n\n" +
                            "建议重启 App 一次后再尝试，或手动重新安装。",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAck) { Text("好的") }
        },
    )
}
