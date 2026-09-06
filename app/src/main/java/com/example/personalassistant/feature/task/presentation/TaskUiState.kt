package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskGroups
import com.example.personalassistant.feature.task.domain.model.TaskSort

/**
 * 任务主页 UI 状态
 *
 * - isLoading：初始状态（DB 还没回值）
 * - groups：P1-P4 + 未排期 + 已完成 分组结果
 * - showCompleted：是否展开底部的"已完成"折叠区
 *
 * Phase 4b 新增：
 * - filter：当前筛选条件（All / ByCategory / ...）
 * - sort：当前排序方式
 * - sortMenuExpanded：排序菜单是否展开（局部 UI 态）
 *
 * Phase 6c 新增：
 * - filter 改为 [FilterExpression] 类型（旧 [TaskFilter] 不删，内部委托）
 * - tagsWithCount：标签云（按频次降序），用于 TagFilterBar 渲染
 * - selectedTagId：当前选中的标签 id（null = 不过滤）
 *
 * Phase 6c 内部约定：filter 与 selectedTagId 不强制组合。
 * - 未来若 selectedTagId 非 null，可由 VM 把 filter 转译为 `And(ByTag(id), filter)`
 * - 当前实现保留两套独立状态，简化 UI 逻辑
 */
data class TaskUiState(
    val isLoading: Boolean = true,
    val groups: TaskGroups = TaskGroups(emptyMap(), emptyList()),
    val showCompleted: Boolean = false,
    val filter: FilterExpression = FilterExpression.ActiveOnly,
    val sort: TaskSort = TaskSort.DEFAULT,
    val sortMenuExpanded: Boolean = false,
    // ===== Phase 6c =====
    val tagsWithCount: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
)