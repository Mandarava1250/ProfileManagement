package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import javax.inject.Inject

/**
 * Phase 15/16：删除清单项。
 *
 * Phase 16 起清单项就是 [Task]（`sub_tasks` 表已并入 `tasks`）。
 */
class DeleteChecklistItemUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    suspend operator fun invoke(item: Task) {
        repository.deleteItem(item)
    }
}
