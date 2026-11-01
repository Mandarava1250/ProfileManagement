package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import javax.inject.Inject

/**
 * Phase 15：新增一个**嵌套**子清单项（挂在另一个清单项下面）。
 *
 * 空白标题会被抛错（与 [AddChecklistItemUseCase] 行为一致）。
 */
class AddNestedChecklistItemUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    suspend operator fun invoke(parentSubTaskId: Long, title: String): Long {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "子清单项内容不能为空" }
        return repository.addNestedItem(parentSubTaskId, trimmed)
    }
}
