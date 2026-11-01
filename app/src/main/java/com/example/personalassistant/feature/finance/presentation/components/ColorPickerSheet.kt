package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * C 重构阶段 6：分类颜色选择 Sheet
 *
 * - 8 色预设（暖红 / 紫 / 蓝紫 / 蓝 / 青 / 绿 / 黄 / 橙）
 * - 横滑 FlowRow + 选中态高亮（primary @ 20% alpha，与 [IconPickerSheet] 一致）
 * - 回调 [onPick] 传入选中的 0xFFXXXXXX 整数色
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerSheet(
    selectedColor: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "选择颜色",
                style = MaterialTheme.typography.titleLarge,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ColorPresetPalette.forEach { entry ->
                    val selected = entry.color == selectedColor
                    val containerColor = if (selected)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = containerColor,
                        onClick = { onPick(entry.color) },
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(28.dp)
                                .padding(0.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(28.dp).padding(0.dp),
                                shape = CircleShape,
                                color = Color(entry.color),
                                content = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 8 色预设，与 IconRegistry 一致保持视觉统一 */
private data class ColorEntry(val color: Int)

private val ColorPresetPalette = listOf(
    ColorEntry(0xFFE57373.toInt()), // 红
    ColorEntry(0xFFBA68C8.toInt()), // 紫
    ColorEntry(0xFF7986CB.toInt()), // 蓝紫
    ColorEntry(0xFF4FC3F7.toInt()), // 蓝
    ColorEntry(0xFF4DB6AC.toInt()), // 青
    ColorEntry(0xFF81C784.toInt()), // 绿
    ColorEntry(0xFFFFD54F.toInt()), // 黄
    ColorEntry(0xFFFF8A65.toInt()), // 橙
)