package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察独立清单项（parentTaskId IS NULL）。
 *
 * Phase 16 起这些就是顶层任务，类型从 `SubTask` 变成 [Task]。
 */
class ObserveStandaloneChecklistUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    operator fun invoke(): Flow<List<Task>> = repository.observeStandaloneChecklist()
}
