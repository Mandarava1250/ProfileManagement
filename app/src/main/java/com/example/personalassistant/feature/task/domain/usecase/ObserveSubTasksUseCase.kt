package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 6b：观察任务下的子清单（详情页 SubTaskSection 用）。
 *
 * 简单包装 [TaskRepository.observeSubTasks]，保持 UseCase 层的接口一致性
 * （让 ViewModel 调 UseCase 而不是直接调 Repository）。
 *
 * Phase 6b：`open` 让 Compose UI / VM 测试 fork 子类覆盖 invoke 直接喂流。
 *
 * Phase 16：子项也是 [Task]（同一张 `tasks` 表）。
 */
open class ObserveSubTasksUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    open operator fun invoke(parentId: Long): Flow<List<Task>> =
        repository.observeSubTasks(parentId)
}
