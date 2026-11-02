package com.example.personalassistant.feature.task.domain.repository

import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.model.TaskListWithCount
import kotlinx.coroutines.flow.Flow

/**
 * Phase 17：清单仓库
 *
 * 清单（`task_lists`）是任务的**归属地**，与标签（跨清单的属性）不同：
 * - 一个任务只属于一个清单
 * - 清单可以增删改，可以归档
 *
 * 与 [com.example.personalassistant.feature.task.domain.repository.TaskRepository] 分开是因为
 * 关注点不同：那边管任务，这边管归属地。
 */
interface TaskListRepository {

    /**
     * 观察全部未归档清单 + 各自的未完成任务数（抽屉用）。
     *
     * 按 sortOrder 升序，内置收集箱排在最前（它 sortOrder = 0 且最早创建）。
     */
    fun observeListsWithPendingCount(): Flow<List<TaskListWithCount>>

    /** 按 id 取清单。 */
    suspend fun getList(id: Long): TaskList?

    /**
     * 新建清单。
     *
     * - 名字会去重（大小写不敏感）：重名时返回**已存在**那条的 id，不新建
     * - sortOrder 自动排到末尾
     *
     * @return 清单 id
     */
    suspend fun createList(
        displayName: String,
        colorHex: Long = DEFAULT_COLOR,
        iconKey: String = "",
    ): Long

    /**
     * 改名（同时更新规范化名）。
     *
     * ⚠️ 内置收集箱不可改名 —— 调用方要自己拦，这里也会 `require` 挡住。
     */
    suspend fun renameList(id: Long, displayName: String)

    /**
     * 换色 / 换图标。
     */
    suspend fun updateAppearance(id: Long, colorHex: Long? = null, iconKey: String? = null)

    /**
     * 删除清单（连带处理它的任务）。
     *
     * ⚠️ `tasks.listId` 对 `task_lists` 是 **ON DELETE CASCADE** —— 直接删清单
     * 会连带删掉它的任务。所以删除必须和「任务怎么办」一起做，不能拆成两步：
     * 拆开的话中间态一崩，用户的任务就无声无息地没了。
     *
     * @param moveTasksToInbox true = 任务先挪到收集箱（任务保住）；false = 任务一并删除
     * @return 被处理（移动或删除）的任务数
     */
    suspend fun deleteListAndHandleTasks(id: Long, moveTasksToInbox: Boolean): Int

    /**
     * 归档 / 取消归档。
     *
     * 归档的清单不在抽屉里显示，但它的任务还在。
     */
    suspend fun setArchived(id: Long, archived: Boolean)

    /**
     * 重排（拖拽抽屉里的清单）。
     *
     * @param orderedIds 顺序即新的 sortOrder 升序
     */
    suspend fun reorderLists(orderedIds: List<Long>)

    /**
     * 把任务移到另一个清单（清单之间挪任务）。
     *
     * @param toListId 目标清单 id
     * @param taskIds 要移动的任务 id
     * @return 实际移动了几条
     */
    suspend fun moveTasksToList(toListId: Long, taskIds: List<Long>): Int

    companion object {
        /** 默认颜色（樱花粉，与默认标签色同款） */
        const val DEFAULT_COLOR = 0xFFFF69B4L
    }
}
