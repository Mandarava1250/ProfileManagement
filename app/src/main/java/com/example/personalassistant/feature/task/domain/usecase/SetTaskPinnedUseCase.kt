package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Phase 24：置顶 / 取消置顶。
 *
 * `tasks.isPinned` 这一列从 v15 就有了，但一直没接上层 ——
 * 写进去也不会生效，因为排序里没有它（见 [com.example.personalassistant.feature.task.data.local.TaskDao]
 * 与 [com.example.personalassistant.feature.task.domain.model.TaskSort]）。
 * Phase 24 把这两处一起补上，置顶才真正「看得见」。
 */
class SetTaskPinnedUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(id: Long, pinned: Boolean) {
        repository.setPinned(id, pinned)
    }
}
