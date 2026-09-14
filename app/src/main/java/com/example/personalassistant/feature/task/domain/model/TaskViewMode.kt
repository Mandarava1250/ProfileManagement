package com.example.personalassistant.feature.task.domain.model

/**
 * 任务主页视图模式（Phase 8b'）
 *
 * - [ALL]：显示现有 5 个分组（P1/P2/P3/P4/UNSCHEDULED）的合并视图；
 *         不限定特定象限，可滚动浏览所有未完成任务
 * - [BY_QUADRANT]：聚焦到单个象限（[TaskDisplayPreferences.selectedQuadrant] 指定）；
 *         只渲染该象限的任务列表 + 已完成折叠区
 *
 * Phase 8final G2 新增：
 * - [MATRIX]：**矩阵视图 / 2×2 分组**。与 [ALL] 显示**完全相同的数据**
 *   （同一份 [TaskGroups]），只是把 4 个分组按 2×2 空间排布，
 *   未排期区作为独立区域呈现。不引入任何新的分类逻辑。
 *
 * 默认值为 [ALL]，避免用户进入看到一片空白。
 *
 * 持久化兼容：DataStore 走 `TaskViewMode.valueOf(name)`，新增枚举值无需 schema 迁移；
 * 读到无法识别的字符串时按既有防御逻辑回退 [ALL]。
 */
enum class TaskViewMode {
    ALL,
    BY_QUADRANT,
    MATRIX,
}