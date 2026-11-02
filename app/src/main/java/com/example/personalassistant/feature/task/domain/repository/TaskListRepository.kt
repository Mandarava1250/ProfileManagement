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
     * 删除清单。
     *
     * ⚠️ `tasks.listId` 对 `task_lists` 是 **ON DELETE CASCADE** —— 删清单会连带删掉
     * 它的任务！所以上层必须在删之前问用户「任务怎么办」（移到收集箱 / 一并删）。
     *
     * ⚠️ 内置收集箱不可删除。
     */
    suspend fun deleteList(id: Long)

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
     * 把任务移到另一个清单（删除清单时「移到收集箱」用）。
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
