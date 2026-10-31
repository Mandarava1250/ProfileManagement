package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.TaskCategory

/**
 * 任务列表顶部 FilterBar
 *
 * - 左侧：横向滚动的 FilterChip —— "全部" + 4 个分类
 * - 右侧：排序图标按钮（点击弹出 [SortMenu]）
 *
 * 当前筛选通过 [FilterExpression.equals] 判断。
 *
 * Phase 4b 设计取舍：分类列表固定为 [TaskCategory.PRESETS]。
 * Phase 6c：[onFilterChange] 改用 [FilterExpression] 类型（同时支持标签筛选的复合）。
 *
 * 未来用户自定义分类接入后，传 `categories` 参数替换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFilterBar(
    currentFilter: FilterExpression,
    currentSort: com.example.personalassistant.feature.task.domain.model.TaskSort,
    categories: List<TaskCategory> = TaskCategory.PRESETS,
    onFilterChange: (FilterExpression) -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Phase 14：排序菜单直接长在排序按钮上，所以菜单的显隐与选择下放到组件内部
    sortMenuExpanded: Boolean = false,
    onSortSelected: (com.example.personalassistant.feature.task.domain.model.TaskSort) -> Unit = {},
    onSortDismiss: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // 滚动 chip 列表
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = 8.dp),
        ) {
            // 「全部」
            FilterChip(
                selected = currentFilter is FilterExpression.All,
                onClick = { onFilterChange(FilterExpression.All) },
                label = { Text("全部") },
                // Phase 14：热区补到 48dp
                modifier = Modifier.heightIn(min = 48.dp),
            )

            // 分类 chips
            categories.forEach { category ->
                val selected = currentFilter is FilterExpression.ByCategory &&
                    currentFilter.categoryName == category.name
                FilterChip(
                    selected = selected,
                    onClick = {
                        onFilterChange(FilterExpression.ByCategory(category.name))
                    },
                    label = { Text(category.name) },
                    // Phase 14：热区补到 48dp
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // Phase 14：排序菜单锚到排序按钮本身
        // （原来菜单挂在列表上方的一个空 Box 里，弹出来跟按钮对不上号）
        Box {
            // 排序图标
            IconButton(onClick = onSortClick) {
                Icon(
                    imageVector = Icons.Rounded.Sort,
                    contentDescription = "排序",
                    tint = if (currentSort != com.example.personalassistant.feature.task.domain.model.TaskSort.DEFAULT)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SortMenu(
                expanded = sortMenuExpanded,
                currentSort = currentSort,
                onSortSelected = onSortSelected,
                onDismiss = onSortDismiss,
            )
        }
    }
}