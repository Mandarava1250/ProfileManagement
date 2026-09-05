package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/** 添加独立清单项（parentTaskId = null）。空白标题会被抛错。 */
class AddStandaloneSubTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(title: String): Long {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "清单项内容不能为空" }
        return repository.addStandaloneSubTask(trimmed)
    }
}
