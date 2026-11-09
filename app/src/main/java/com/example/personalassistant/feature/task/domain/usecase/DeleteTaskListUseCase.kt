package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.repository.TaskListRepository
import javax.inject.Inject

/**
 * Phase 17：删除清单
 *
 * ⚠️ **危险操作**：`tasks.listId` 对 `task_lists` 是 ON DELETE CASCADE，
 * 删清单会连带删掉它的任务。
 *
 * 所以这个用例提供两种策略，由调用方（UI）显式选：
 * - [DeleteStrategy.MOVE_TASKS_TO_INBOX]：任务先挪到收集箱，再删清单（任务保住）
 * - [DeleteStrategy.DELETE_TASKS_TOO]：直接删清单，任务一起没了
 *
 * 「挪任务」和「删清单」在仓库层是同一个原子操作 —— 拆成两步的话中间态一崩，
 * 用户的任务就无声无息地没了。
 */
open class DeleteTaskListUseCase @Inject constructor(
    private val repository: TaskListRepository,
) {

    enum class DeleteStrategy {
        /** 任务移到收集箱（推荐 —— 不丢东西） */
        MOVE_TASKS_TO_INBOX,

        /** 任务一并删除 */
        DELETE_TASKS_TOO,
    }

    /**
     * @param strategy 任务怎么处理（见 [DeleteStrategy]）
     * @return 被保留下来的任务数（挪到收集箱的那些）
     */
    open suspend operator fun invoke(
        id: Long,
        strategy: DeleteStrategy = DeleteStrategy.MOVE_TASKS_TO_INBOX,
    ): Int {
        require(id != TaskList.INBOX_ID) { "内置收集箱不可删除" }
        return repository.deleteListAndHandleTasks(
            id = id,
            moveTasksToInbox = strategy == DeleteStrategy.MOVE_TASKS_TO_INBOX,
        )
    }
}
