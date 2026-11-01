package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.ChecklistFilter
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Tag

/**
 * 清单页 UI 状态
 *
 * ## Phase 15 扩展
 *
 * - [items]：**旧字段**（扁平、已过滤的 [SubTask] 列表），保留是为了让现有 UI 不用改就能编译。
 *   ⚠️ 新 UI 请用 [itemDetails] / [sections]（带标签与嵌套子项）。
 * - [itemDetails]：带标签 + 嵌套子清单的清单项（已筛选 + 已排序）
 * - [sections]：按当前分组方式聚合的结果（group = NONE 时只有一个 section）
 * - [filter]：当前筛选条件（分类 / 标签 / 分组 / 排序）
 * - [availableTags]：全部标签（UI 画标签筛选 chip 行用）
 */
data class ChecklistUiState(
    val isLoading: Boolean = true,
    /** 旧 UI 兼容字段（扁平清单项）。新 UI 用 [itemDetails]。 */
    val items: List<SubTask> = emptyList(),
    /** 带标签与嵌套子项的清单项（已筛选 + 已排序）。 */
    val itemDetails: List<ChecklistItemUi> = emptyList(),
    /** 分组结果（按 [filter] 的分组方式聚合）。 */
    val sections: List<ChecklistSectionUi> = emptyList(),
    /** 当前筛选条件。 */
    val filter: ChecklistFilter = ChecklistFilter(),
    /** 全部标签（标签筛选 chip 行用）。 */
    val availableTags: List<Tag> = emptyList(),
)
