package com.example.personalassistant.feature.task.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.task.domain.model.ChecklistFilter
import com.example.personalassistant.feature.task.domain.model.ChecklistGroup
import com.example.personalassistant.feature.task.domain.model.ChecklistSort
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.TaskCategory
import com.example.personalassistant.feature.task.presentation.components.SheetSection

/**
 * 清单页的「筛选 / 分组 / 排序」底部面板（Phase 15）。
 *
 * 视觉语言**完全复用**任务页的 `TaskFilterSheet`：
 * 同样的 ModalBottomSheet + Column + 小节标题（[SheetSection]）+ FlowRow 里的 FilterChip。
 * 两个面板看起来是一套东西，只是分段内容不同。
 *
 * @param filter 当前筛选条件
 * @param availableTags 可选标签
 * @param onCategoryFilterChange 分类筛选（null = 不筛）
 * @param onTagFilterChange 标签筛选（null = 不筛）
 * @param onGroupChange 分组方式
 * @param onSortChange 排序方式
 * @param onReset 重置全部条件
 * @param onDismiss 关闭
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChecklistFilterSheet(
    filter: ChecklistFilter,
    availableTags: List<Tag>,
    onCategoryFilterChange: (String?) -> Unit,
    onTagFilterChange: (Long?) -> Unit,
    onGroupChange: (ChecklistGroup) -> Unit,
    onSortChange: (ChecklistSort) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            // ===== 分类 =====
            SheetSection(title = stringResource(R.string.checklist_filter_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                FilterChip(
                    selected = filter.category == null,
                    onClick = { onCategoryFilterChange(null) },
                    label = { Text(stringResource(R.string.action_all)) },
                )
                TaskCategory.PRESETS.forEach { category ->
                    FilterChip(
                        selected = filter.category == category.name,
                        onClick = { onCategoryFilterChange(category.name) },
                        label = { Text(category.name) },
                    )
                }
            }

            // ===== 标签 =====
            SheetSection(title = stringResource(R.string.checklist_filter_tag))
            if (availableTags.isEmpty()) {
                Text(
                    text = stringResource(R.string.checklist_no_tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    FilterChip(
                        selected = filter.tagId == null,
                        onClick = { onTagFilterChange(null) },
                        label = { Text(stringResource(R.string.checklist_filter_unlimited)) },
                    )
                    availableTags.forEach { tag ->
                        FilterChip(
                            selected = filter.tagId == tag.id,
                            onClick = { onTagFilterChange(tag.id) },
                            label = { Text(tag.displayName) },
                        )
                    }
                }
            }

            // ===== 分组 =====
            SheetSection(title = stringResource(R.string.checklist_filter_group))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                ChecklistGroup.entries.forEach { group ->
                    FilterChip(
                        selected = filter.group == group,
                        onClick = { onGroupChange(group) },
                        label = { Text(stringResource(group.labelRes())) },
                    )
                }
            }

            // ===== 排序 =====
            SheetSection(title = stringResource(R.string.checklist_filter_sort))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                ChecklistSort.entries.forEach { sort ->
                    FilterChip(
                        selected = filter.sort == sort,
                        onClick = { onSortChange(sort) },
                        label = { Text(stringResource(sort.labelRes())) },
                    )
                }
            }

            // ===== 重置 =====
            TextButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.checklist_filter_reset))
            }
        }
    }
}

/** 分组方式的展示文案。 */
private fun ChecklistGroup.labelRes(): Int = when (this) {
    ChecklistGroup.NONE -> R.string.checklist_group_none
    ChecklistGroup.UNCOMPLETED -> R.string.checklist_group_uncompleted
    ChecklistGroup.COMPLETED -> R.string.checklist_group_completed
    ChecklistGroup.BY_CATEGORY -> R.string.checklist_group_by_category
    ChecklistGroup.BY_DUE_DATE -> R.string.checklist_group_by_due_date
}

/** 排序方式的展示文案。 */
private fun ChecklistSort.labelRes(): Int = when (this) {
    ChecklistSort.MANUAL -> R.string.checklist_sort_manual
    ChecklistSort.PRIORITY -> R.string.checklist_sort_priority
    ChecklistSort.DUE_DATE -> R.string.checklist_sort_due_date
    ChecklistSort.CREATED_DESC -> R.string.checklist_sort_created_desc
    ChecklistSort.TITLE_ASC -> R.string.checklist_sort_title_asc
}
