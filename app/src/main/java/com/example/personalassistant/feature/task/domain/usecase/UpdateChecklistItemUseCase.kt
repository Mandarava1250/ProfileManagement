package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import javax.inject.Inject

/**
 * Phase 15：部分更新清单项。
 *
 * ## 语义
 *
 * - 传 null = **不修改**该字段（保持原值）
 * - 想把某个可空字段**清空**（分类 / 截止时间 / 金额）→ 传对应的 `clearXxx = true`
 *
 * 之所以要额外的 clear 开关：null 同时表示「不改」和「清空」的话，
 * UI 就没法把分类/截止时间/金额删掉了。
 */
class UpdateChecklistItemUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    suspend operator fun invoke(
        id: Long,
        title: String? = null,
        category: String? = null,
        dueDate: Long? = null,
        priority: TaskPriority? = null,
        amount: Double? = null,
        clearCategory: Boolean = false,
        clearDueDate: Boolean = false,
        clearAmount: Boolean = false,
    ) {
        val current = repository.getItem(id) ?: return
        val updatedTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: current.title
        repository.updateItem(
            current.copy(
                title = updatedTitle,
                category = if (clearCategory) null else category ?: current.category,
                dueDate = if (clearDueDate) null else dueDate ?: current.dueDate,
                priority = priority ?: current.priority,
                amount = if (clearAmount) null else amount ?: current.amount,
            ),
        )
    }
}
