package com.example.personalassistant.feature.finance.presentation.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.presentation.util.IconRegistry

/**
 * C 重构阶段 7：分类行
 *
 * - 左侧色块可点 → [onChangeColor]（弹 ColorPickerSheet）
 * - 中间文本可点 → [onRename]（弹 RenameCategoryDialog）
 * - 右侧 Edit 按钮 → [onEditIcon]（弹 IconPickerSheet）
 * - 非默认分类才显示「删除」按钮 → [onDelete]
 */
@Composable
internal fun CategoryRow(
    category: Category,
    onDelete: () -> Unit,
    onEditIcon: () -> Unit,
    onRename: () -> Unit,
    onChangeColor: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 色块可点 → 改颜色
        Surface(
            shape = CircleShape,
            color = Color(category.color),
            modifier = Modifier.padding(2.dp),
            onClick = onChangeColor,
        ) {
            Icon(
                imageVector = IconRegistry.resolve(category.iconName),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(8.dp),
            )
        }

        // 中间文字 + 副标题可点 → 改名
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRename),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (category.type == TransactionType.EXPENSE) "支出分类" else "收入分类",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        // 改图标
        IconButton(onClick = onEditIcon) {
            Icon(Icons.Rounded.Edit, contentDescription = "更换图标")
        }

        // 非默认：删 / 默认：文字标识
        if (!category.isDefault) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Text(
                text = "默认",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}