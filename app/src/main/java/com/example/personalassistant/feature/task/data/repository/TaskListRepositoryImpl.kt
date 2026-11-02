package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskListDao
import com.example.personalassistant.feature.task.data.local.TaskListEntity
import com.example.personalassistant.feature.task.data.mapper.toDomain
import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.model.TaskListWithCount
import com.example.personalassistant.feature.task.domain.repository.TaskListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 17：清单仓库实现
 *
 * - 所有 IO 走 [dispatcher]（IoDispatcher）
 * - 不依赖 [com.example.personalassistant.core.database.AppDatabase]：
 *   这样它能在 JVM 单测里用 Fake DAO 直接跑
 */
@Singleton
class TaskListRepositoryImpl @Inject constructor(
    private val taskListDao: TaskListDao,
    private val taskDao: TaskDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : TaskListRepository {

    override fun observeListsWithPendingCount(): Flow<List<TaskListWithCount>> =
        taskListDao.observeWithPendingCount()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatcher)

    override suspend fun getList(id: Long): TaskList? = withContext(dispatcher) {
        taskListDao.getById(id)?.toDomain()
    }

    /**
     * 新建清单。
     *
     * 名字去重：`insert` 用 `OnConflictStrategy.IGNORE`，重名时返回 -1，
     * 这时按规范化名找回来返回已有那条的 id —— 不静默吞掉，也不建重复的。
     */
    override suspend fun createList(
        displayName: String,
        colorHex: Long,
        iconKey: String,
    ): Long = withContext(dispatcher) {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "清单名不能为空" }

        val normalized = trimmed.lowercase()
        // 先查一次：命中就直接返回，避免依赖 insert 的返回值语义
        taskListDao.findByNormalizedName(normalized)?.let { return@withContext it.id }

        val nextOrder = (taskListDao.getMaxSortOrder() ?: -1) + 1
        val rowId = taskListDao.insert(
            TaskListEntity(
                nameNormalized = normalized,
                displayName = trimmed,
                colorHex = colorHex,
                iconKey = iconKey,
                sortOrder = nextOrder,
            )
        )
        if (rowId > 0L) {
            rowId
        } else {
            // IGNORE 撞名：把已存在的那条找出来
            taskListDao.findByNormalizedName(normalized)?.id
                ?: error("新建清单失败：既没插进去也找不到同名清单")
        }
    }

    override suspend fun renameList(id: Long, displayName: String) = withContext(dispatcher) {
        require(id != TaskList.INBOX_ID) { "内置收集箱不可改名" }
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "清单名不能为空" }

        val current = taskListDao.getById(id) ?: return@withContext
        val normalized = trimmed.lowercase()
        // 重名检查：不能撞上别的清单
        taskListDao.findByNormalizedName(normalized)?.let { existing ->
            require(existing.id == id) { "已存在同名清单「${existing.displayName}」" }
        }
        taskListDao.update(
            current.copy(nameNormalized = normalized, displayName = trimmed)
        )
    }

    override suspend fun updateAppearance(
        id: Long,
        colorHex: Long?,
        iconKey: String?,
    ) = withContext(dispatcher) {
        require(id != TaskList.INBOX_ID) { "内置收集箱不可改外观" }
        val current = taskListDao.getById(id) ?: return@withContext
        taskListDao.update(
            current.copy(
                colorHex = colorHex ?: current.colorHex,
                iconKey = iconKey ?: current.iconKey,
            )
        )
    }

    /**
     * 删除清单（连带处理它的任务）。
     *
     * ⚠️ `tasks.listId` 对 `task_lists` 是 CASCADE —— 所以这里**必须**把
     * 「任务怎么办」和「删清单」放在同一个方法里做：
     * 先把任务挪到收集箱，再删清单；顺序反了任务就跟着清单一起没了。
     */
    override suspend fun deleteListAndHandleTasks(id: Long, moveTasksToInbox: Boolean): Int =
        withContext(dispatcher) {
            require(id != TaskList.INBOX_ID) { "内置收集箱不可删除" }

            val taskIds = taskDao.getTaskIdsByList(id)
            val handled = if (moveTasksToInbox) {
                if (taskIds.isEmpty()) 0 else taskDao.updateListIds(taskIds, TaskList.INBOX_ID)
            } else {
                // 任务一并删除：交给 CASCADE 处理，不额外查
                0
            }
            taskListDao.deleteById(id)
            handled
        }

    override suspend fun setArchived(id: Long, archived: Boolean) = withContext(dispatcher) {
        require(id != TaskList.INBOX_ID) { "内置收集箱不可归档" }
        taskListDao.setArchived(id, archived)
    }

    override suspend fun reorderLists(orderedIds: List<Long>) = withContext(dispatcher) {
        orderedIds.forEachIndexed { index, id ->
            taskListDao.setSortOrder(id, index)
        }
    }

    override suspend fun moveTasksToList(toListId: Long, taskIds: List<Long>): Int =
        withContext(dispatcher) {
            if (taskIds.isEmpty()) return@withContext 0
            taskDao.updateListIds(taskIds, toListId)
        }
}
