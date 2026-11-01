package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/** 切换清单项完成状态。 */
open class ToggleSubTaskCompleteUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    open suspend operator fun invoke(id: Long, completed: Boolean) =
        repository.toggleSubTaskComplete(id, completed)
}
