package com.example.personalassistant.feature.finance.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.presentation.components.IconPickerSheet
import com.example.personalassistant.feature.finance.presentation.util.IconRegistry

/**
 * C 重构阶段 7：新增分类对话框
 *
 * - 名称输入（沿用旧）
 * - 收入/支出选择（沿用旧）
 * - **新增**：图标预览，点击弹 [IconPickerSheet]
 * - **新增**：色块预览，点击弹 ColorPickerSheet（由 UI 层另外拉起，本组件仅暴露 [selectedColor]）
 *
 * @param onConfirm 把 name/type/iconName/color 一起回传；UI 再调 ViewModel.addCategory
 */
@Composable
internal fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: TransactionType, iconName: String, color: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var iconName by remember { mutableStateOf("Category") }
    var color by remember { mutableStateOf(0xFF6750A4.toInt()) }
    var showIconPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == TransactionType.EXPENSE,
                        onClick = { type = TransactionType.EXPENSE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("支出") }
                    SegmentedButton(
                        selected = type == TransactionType.INCOME,
                        onClick = { type = TransactionType.INCOME },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("收入") }
                }

                // 图标预览 + 入口
                Surface(
                    shape = CircleShape,
                    color = Color(color),
                    onClick = { showIconPicker = true },
                    modifier = Modifier.padding(2.dp),
                ) {
                    Icon(
                        imageVector = IconRegistry.resolve(iconName),
                        contentDescription = "选择图标",
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, type, iconName, color) },
                enabled = name.isNotBlank(),
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (showIconPicker) {
        IconPickerSheet(
            selectedIconName = iconName,
            onPick = { picked ->
                iconName = picked
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }
}