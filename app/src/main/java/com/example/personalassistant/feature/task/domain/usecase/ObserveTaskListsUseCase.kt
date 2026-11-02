package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.TaskListWithCount
import com.example.personalassistant.feature.task.domain.repository.TaskListRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 17：观察全部清单 + 未完成数（抽屉列表）
 */
open class ObserveTaskListsUseCase @Inject constructor(
    private val repository: TaskListRepository,
) {
    open operator fun invoke(): Flow<List<TaskListWithCount>> =
        repository.observeListsWithPendingCount()
}
