package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.presentation.util.IconRegistry
import com.example.personalassistant.core.ui.theme.onColorFor

/**
 * Phase 7a：分类选择 chip（用于 AddTransactionSheet）
 *
 * - 内置彩色圆形 + icon
 * - 选中状态：背景填充；未选中：边框样式
 */
@Composable
fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(category.color)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category.name) },
        leadingIcon = {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = color,
            ) {
                Icon(
                    imageVector = IconRegistry.resolve(category.iconName),
                    contentDescription = null,
                    tint = onColorFor(color),
                    modifier = Modifier.padding(2.dp),
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = color,
        ),
        modifier = modifier,
    )
}

/** 简化版：直接给列表用的 Row + wrap 布局 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChipRow(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { cat ->
            CategoryChip(
                category = cat,
                selected = selectedId == cat.id,
                onClick = { onSelect(cat.id) },
            )
        }
    }
}
