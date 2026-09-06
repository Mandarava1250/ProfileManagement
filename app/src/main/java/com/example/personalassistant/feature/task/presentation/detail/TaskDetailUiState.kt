package com.example.personalassistant.feature.task.presentation.detail

import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task

/**
 * Phase 6b：任务详情页 UI 状态
 *
 * 设计动机：详情页按 sections（BasicInfo / Reminder / SubTask / Tags占位 / Attachments占位）拆分，
 * 单一 UiState 聚合 sections 需要的字段，避免 section 各自订阅多个 ViewModel。
 *
 * - isLoading：首次加载 / task 还未到达（combine 第一帧之前）
 * - task：null = task 已被删除，UI 监听到后会 popBackStack
 * - subTasks：当前任务下的子清单（来自 observeSubTasks 流）
 * - newSubTaskInput：子任务输入框文本（按 Phase 5b TaskEditUiState 同模式）
 *
 * Sections 模式的优势（plan v3.1 扩展点）：
 * - 未来加 Tags section / Attachments section 只需新增字段（如 tags: List<Tag>）
 * - 各 section 独立 Composable，从 UiState 拿自己关心的字段，互不耦合
 */
data class TaskDetailUiState(
    val isLoading: Boolean = true,
    val task: Task? = null,
    val subTasks: List<SubTask> = emptyList(),
    val newSubTaskInput: String = "",
    val errorMessage: String? = null,
) {
    /** task 是否存在（不存在 = 触发 popBackStack） */
    val exists: Boolean get() = task != null

    /** 子清单完成进度，用于 Section 标题右侧 badge */
    val completedSubTaskCount: Int get() = subTasks.count { it.isCompleted }
    val totalSubTaskCount: Int get() = subTasks.size
}
