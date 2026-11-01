package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import javax.inject.Inject

/** Phase 15：给清单项挂标签（重复挂载静默忽略）。 */
class AttachTagToChecklistItemUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    suspend operator fun invoke(subTaskId: Long, tagId: Long) {
        repository.attachTag(subTaskId, tagId)
    }
}
