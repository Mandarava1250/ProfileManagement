package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * 删除清单项。
 *
 * Phase 16 起子项也是 [Task]（同一张 `tasks` 表，用 `parentTaskId` 表达层级）。
 */
open class DeleteSubTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    open suspend operator fun invoke(subTask: Task) = repository.deleteSubTask(subTask)
}
