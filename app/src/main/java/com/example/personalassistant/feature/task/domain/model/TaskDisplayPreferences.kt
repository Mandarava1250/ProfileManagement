package com.example.personalassistant.feature.task.domain.model

/**
 * 任务显示偏好（Phase 8b'）
 *
 * 用户级别的「视图与档名」配置。持久化到 DataStore（与 [Task] 数据隔离）。
 *
 * 字段：
 * - [aliases]：每个 [TaskQuadrant] 的展示别名（默认 = enum.label）。
 *              用户长按 Tab 可改写；不改 priority 数值、不动 enum 顺序。
 * - [viewMode]：当前 Tab 选中模式（[TaskViewMode.ALL] 或 [TaskViewMode.BY_QUADRANT]）
 * - [selectedQuadrant]：[BY_QUADRANT] 模式下聚焦哪个象限；
 *                       [ALL] 模式下固定为 null（不渲染具体象限）
 *
 * 不可变 data class：更新通过 `copy(...)` + Repository 接口完成。
 */
data class TaskDisplayPreferences(
    val aliases: Map<TaskQuadrant, String> = DEFAULT_ALIASES,
    val viewMode: TaskViewMode = TaskViewMode.ALL,
    val selectedQuadrant: TaskQuadrant? = null,
) {
    companion object {
        /**
         * 默认别名 = enum.label。
         *
         * 注意：每次都重新构造一个 Map，避免外界 mutate DEFAULT_ALIASES。
         */
        val DEFAULT_ALIASES: Map<TaskQuadrant, String> =
            TaskQuadrant.entries.associateWith { it.label }

        /**
         * 取一个象限的展示别名；未配置时回退到 enum.label。
         */
        fun aliasOf(prefs: TaskDisplayPreferences, quadrant: TaskQuadrant): String =
            prefs.aliases[quadrant] ?: quadrant.label
    }
}