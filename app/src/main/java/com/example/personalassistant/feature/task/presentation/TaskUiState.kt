package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskGroups
import com.example.personalassistant.feature.task.domain.model.TaskMatrix
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskViewMode

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
 *
 * Phase 8b' 新增：
 * - displayPreferences：用户级别的别名 / 视图模式 / 选中象限（持久化到 DataStore）
 * - [activeQuadrants] 辅助函数告诉 UI 当前模式下该渲染哪些象限
 * - [activeTaskCount] 辅助函数：BY_QUADRANT 模式下展示该象限任务数，ALL 模式返回全量 active 数
 *
 * Phase 24 新增：
 * - [allActiveIds]：**全部未完成顶层任务**的 id（按当前排序，**不按象限过滤**）——
 *   拖拽排序时用它保住别的象限里那些项的位置，落库的 manualOrder 也是全局下标。
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
    // ===== Phase 8b' =====
    val displayPreferences: TaskDisplayPreferences = TaskDisplayPreferences(),
    // ===== Phase 24 =====
    val allActiveIds: List<Long> = emptyList(),
) {
    /**
     * 当前模式下应当渲染的象限顺序（用于 TabRow 与列表渲染）：
     * - [TaskViewMode.ALL]：所有象限
     * - [TaskViewMode.BY_QUADRANT]：只渲染 selectedQuadrant（若 null → P1 兜底）
     */
    val activeQuadrants: List<TaskQuadrant>
        get() = when (displayPreferences.viewMode) {
            TaskViewMode.ALL, TaskViewMode.MATRIX -> TaskQuadrant.entries.toList()
            TaskViewMode.BY_QUADRANT -> listOfNotNull(
                displayPreferences.selectedQuadrant ?: TaskQuadrant.P1,
            )
        }

    /**
     * Phase 8final G2：矩阵视图渲染数据。
     *
     * 由 [TaskMatrix.from] 从 [groups] 派生（纯函数、无新分类逻辑）。
     * 每次读取重新构造，避免在 data class 里缓存可变状态；
     * 上游 groups 本身已由 Flow 驱动，重组成本可忽略。
     */
    val matrix: TaskMatrix
        get() = TaskMatrix.from(groups)

    /**
     * 当前选中 Tab 的任务数（给 TabRow 右上角小角标用）：
     * - [TaskViewMode.ALL]：未完成任务总数
     * - [TaskViewMode.BY_QUADRANT]：对应象限的任务数（若没选中 → P1 数）
     */
    val activeTaskCount: Int
        get() {
            val quadrants = activeQuadrants
            return quadrants.sumOf { groups.byQuadrant[it]?.size ?: 0 }
        }
}