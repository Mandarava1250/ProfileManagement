package com.example.personalassistant.feature.task.domain.model

/**
 * 任务主页视图模式（Phase 8b'）
 *
 * - [ALL]：显示现有 5 个分组（P1/P2/P3/P4/UNSCHEDULED）的合并视图；
 *         不限定特定象限，可滚动浏览所有未完成任务
 * - [BY_QUADRANT]：聚焦到单个象限（[TaskDisplayPreferences.selectedQuadrant] 指定）；
 *         只渲染该象限的任务列表 + 已完成折叠区
 *
 * 默认值为 [ALL]，避免用户进入看到一片空白。
 */
enum class TaskViewMode {
    ALL,
    BY_QUADRANT,
}