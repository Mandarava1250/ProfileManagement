package com.example.personalassistant.feature.task.presentation.components

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.TaskCategory
import com.example.personalassistant.feature.task.domain.model.TaskSort

/**
 * 任务页的「筛选与排序」底部面板 —— Phase 14 阶段六 6.3b
 *
 * ## 为什么有这个面板
 *
 * 任务页首屏以前堆着 5 层控件：分类 chips、标签云、象限 Tab、列表/矩阵切换、排序菜单。
 * 一屏放不下，真正要用的列表被挤到下面去，手指要来回划好几趟。
 *
 * 现在首屏只留「象限 Tab + 筛选按钮」，**分类 / 标签 / 排序 / 视图**四段
 * 折叠进这个面板 —— 象限是任务分组的主导航，留在外面；其余四项是"调整条件"，
 * 按需打开。
 *
 * ## 标签与视图用槽位传入
 *
 * [tagContent] / [viewContent] 由调用方填（分别是 [TagFilterBar] 与
 * [MatrixViewToggle]）—— 这样面板不必知道它们的数据类型，
 * 两个组件也各自留在自己的文件里。
 *
 * @param filter 当前分类筛选
 * @param sort 当前排序
 * @param onFilterChange 分类变更
 * @param onSortSelected 排序变更
 * @param tagContent 标签区
 * @param viewContent 列表 / 矩阵切换
 * @param onDismiss 关闭面板
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskFilterSheet(
    filter: FilterExpression,
    sort: TaskSort,
    onFilterChange: (FilterExpression) -> Unit,
    onSortSelected: (TaskSort) -> Unit,
    tagContent: @Composable () -> Unit,
    viewContent: @Composable () -> Unit,
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
            SheetSection(title = stringResource(R.string.task_filter_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                FilterChip(
                    selected = filter is FilterExpression.All,
                    onClick = { onFilterChange(FilterExpression.All) },
                    label = { Text(stringResource(R.string.action_all)) },
                )
                TaskCategory.PRESETS.forEach { category ->
                    val selected = filter is FilterExpression.ByCategory &&
                        filter.categoryName == category.name
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChange(FilterExpression.ByCategory(category.name)) },
                        label = { Text(category.name) },
                    )
                }
            }

            SheetSection(title = stringResource(R.string.task_filter_tag))
            tagContent()

            SheetSection(title = stringResource(R.string.task_filter_sort))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                TaskSort.entries.forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { onSortSelected(option) },
                        label = { Text(option.displayLabel()) },
                    )
                }
            }

            SheetSection(title = stringResource(R.string.task_filter_view))
            viewContent()
        }
    }
}

/**
 * 面板里的小节标题。
 *
 * Phase 15：清单页的筛选面板（`ChecklistFilterSheet`）复用同一套视觉语言，
 * 所以这里从 private 放开成 internal —— 两个面板的标题长得一模一样。
 */
@Composable
internal fun SheetSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = AppSpacing.sm),
    )
}
