package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import javax.inject.Inject

/** Phase 15：从清单项解绑标签。 */
class DetachTagFromChecklistItemUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    suspend operator fun invoke(subTaskId: Long, tagId: Long) {
        repository.detachTag(subTaskId, tagId)
    }
}
