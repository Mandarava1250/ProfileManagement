package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 通用筛选 + 排序 的观察任务流。
 *
 * - 内部直接走 [TaskRepository.observeTasks]，让 Repository 决定怎么走索引
 * - 这是 UseCase 层的"标准入口"，Phase 4a 主页可以选用它替代 [ObserveTasksByQuadrantUseCase]
 *
 * Phase 4a：UI 暂时仍用 ObserveTasksByQuadrantUseCase（保持现有四象限主页 UI），
 * 本 UseCase 作为未来 filter chip / 排序切换的入口预留。
 *
 * Phase 6c：新增 [FilterExpression] 重载，支持 tag 筛选 + 复合 case。
 */
class ObserveTasksUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    /** 旧路径：接 [TaskFilter]（向后兼容） */
    operator fun invoke(
        filter: TaskFilter = TaskFilter.ActiveOnly,
        sort: TaskSort = TaskSort.DEFAULT,
    ): Flow<List<Task>> = repository.observeTasks(filter, sort)

    /** 新路径：接 [FilterExpression]（支持 tag 筛选 + And/Or/Not） */
    operator fun invoke(
        filter: FilterExpression,
        sort: TaskSort = TaskSort.DEFAULT,
    ): Flow<List<Task>> = repository.observeTasks(filter, sort)
}