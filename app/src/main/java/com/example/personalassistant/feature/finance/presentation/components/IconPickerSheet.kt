package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.presentation.util.IconRegistry

/**
 * Phase 7b：分类图标选择 Sheet
 *
 * - 横滑 FlowRow 展示 IconRegistry.availableIconNames 全部 16 个图标
 * - 点击图标回调 [onPick]
 * - 当前选中图标高亮（通过 [selectedIconName]）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerSheet(
    selectedIconName: String?,
    onPick: (String) -> Unit,
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
                text = "选择图标",
                style = MaterialTheme.typography.titleLarge,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconRegistry.availableIconNames.forEach { name ->
                    val selected = name == selectedIconName
                    val containerColor = if (selected)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = containerColor,
                        onClick = { onPick(name) },
                    ) {
                        Icon(
                            imageVector = IconRegistry.resolve(name),
                            contentDescription = name,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}