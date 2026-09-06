package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/** 切换任务完成状态。 */
class ToggleTaskCompleteUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(id: Long, completed: Boolean) =
        repository.toggleTaskComplete(id, completed)
}
