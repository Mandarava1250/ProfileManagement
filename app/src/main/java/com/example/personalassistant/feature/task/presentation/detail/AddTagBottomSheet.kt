package com.example.personalassistant.feature.task.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.presentation.util.TagPalette
import com.example.personalassistant.feature.task.presentation.util.TagPaletteSource
import kotlinx.coroutines.launch

/**
 * Phase 6c：「添加标签」底部表单（BottomSheet）。
 *
 * - 输入 displayName（trim + 非空校验）
 * - 6 色板选择（默认 Pink）
 * - 「确定」-> onCreateTag(name, colorHex)
 *
 * BottomSheet 自身管理：可见性 + 输入框 / 选色临时状态。
 * 由 [com.example.personalassistant.feature.task.presentation.detail.sections.TagSection] 通过 showAddSheet 状态控制显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagBottomSheet(
    visible: Boolean,
    paletteSource: TagPaletteSource,
    onDismiss: () -> Unit,
    onCreateTag: (displayName: String, colorHex: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val palette = remember { paletteSource.all() }

    var input by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf(palette.first().hex) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visible) {
        // 每次重新打开时清空输入
        input = ""
        selectedHex = palette.first().hex
        errorText = null
    }

    fun dismissSheet() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "新建标签",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            // 输入框
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    errorText = null
                },
                placeholder = { Text("标签名（如：Work）") },
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "选择颜色",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            // 色板 row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                palette.forEach { entry ->
                    ColorSwatch(
                        entry = entry,
                        selected = entry.hex == selectedHex,
                        onClick = { selectedHex = entry.hex },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { dismissSheet() }) {
                    Text("取消")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val trimmed = input.trim()
                        when {
                            trimmed.isEmpty() -> {
                                errorText = "标签名不能为空"
                            }
                            else -> {
                                onCreateTag(trimmed, selectedHex)
                                dismissSheet()
                            }
                        }
                    },
                    enabled = input.trim().isNotEmpty(),
                ) {
                    Text("确定")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 单个色板圆点（带选中态对勾）。
 */
@Composable
private fun ColorSwatch(
    entry: TagPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(entry.hex)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选",
                tint = if (entry.isLightColor()) Color.Black else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 同 [com.example.personalassistant.feature.task.presentation.components.TagChip.onContainerColorFor] 的判断逻辑。
 * 简单就地实现避免跨文件引用（两份颜色判定逻辑都简短，独立维护无负担）。
 */
private fun TagPalette.isLightColor(): Boolean {
    val r = ((hex shr 16) and 0xFF).toInt()
    val g = ((hex shr 8) and 0xFF).toInt()
    val b = (hex and 0xFF).toInt()
    val brightness = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
    return brightness > 0.6f
}