package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import javax.inject.Inject

/** Phase 15：勾选 / 取消勾选清单项。 */
class ToggleChecklistItemUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    suspend operator fun invoke(id: Long, completed: Boolean) {
        repository.toggleComplete(id, completed)
    }
}
