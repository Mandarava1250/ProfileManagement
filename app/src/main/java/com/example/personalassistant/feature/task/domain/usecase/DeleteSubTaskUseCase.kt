package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/** 删除清单项。 */
open class DeleteSubTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    open suspend operator fun invoke(subTask: SubTask) = repository.deleteSubTask(subTask)
}
