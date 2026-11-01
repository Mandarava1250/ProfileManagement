package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/** 删除任务。子清单由 FK SET_NULL 自动转为独立项。 */
open class DeleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    open suspend operator fun invoke(task: Task) = repository.deleteTask(task)
}
