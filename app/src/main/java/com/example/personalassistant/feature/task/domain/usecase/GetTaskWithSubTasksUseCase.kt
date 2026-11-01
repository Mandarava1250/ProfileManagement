package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/** 获取任务及其子清单（编辑页用）。id 不存在时返回 null。 */
open class GetTaskWithSubTasksUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    open suspend operator fun invoke(id: Long): TaskWithSubTasks? =
        repository.getTaskWithSubTasks(id)
}
