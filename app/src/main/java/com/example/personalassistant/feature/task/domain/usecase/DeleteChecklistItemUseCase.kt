package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import javax.inject.Inject

/** Phase 15：删除清单项。 */
class DeleteChecklistItemUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    suspend operator fun invoke(item: SubTask) {
        repository.deleteItem(item)
    }
}
