package com.example.personalassistant.feature.task.presentation.detail

import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.Task

/**
 * Phase 6b→6c：任务详情页 UI 状态
 *
 * 设计动机：详情页按 sections（BasicInfo / SubTask / Tag / Attachments）拆分，
 * 单一 UiState 聚合 sections 需要的字段，避免 section 各自订阅多个 ViewModel。
 *
 * - isLoading：首次加载 / task 还未到达（combine 第一帧之前）
 * - task：null = task 已被删除，UI 监听到后会 popBackStack
 * - subTasks：当前任务下的子清单（来自 observeSubTasks 流）
 * - newSubTaskInput：子任务输入框文本
 * - tags：当前任务关联的标签（Phase 6c 新增，来自 observeTagsForTask 流）
 * - errorMessage：一次性错误（snackbar 用）
 *
 * Sections 模式的优势（plan v3.1 扩展点）：
 * - 未来加 Attachments section 只需新增字段，互不耦合
 */
data class TaskDetailUiState(
    val isLoading: Boolean = true,
    val task: Task? = null,
    val subTasks: List<SubTask> = emptyList(),
    val newSubTaskInput: String = "",
    val tags: List<Tag> = emptyList(),
    val errorMessage: String? = null,
) {
    /** task 是否存在（不存在 = 触发 popBackStack） */
    val exists: Boolean get() = task != null

    /** 子清单完成进度，用于 Section 标题右侧 badge */
    val completedSubTaskCount: Int get() = subTasks.count { it.isCompleted }
    val totalSubTaskCount: Int get() = subTasks.size
}