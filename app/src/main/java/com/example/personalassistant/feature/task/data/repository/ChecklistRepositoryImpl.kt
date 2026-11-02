package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.task.data.mapper.toDomain
import com.example.personalassistant.feature.task.data.mapper.toEntity
import com.example.personalassistant.feature.task.domain.model.ChecklistItem
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 清单仓库实现
 *
 * - 读：一次拿「全部任务（含子项）+ 全部标签关联 + 全部标签」，
 *   在内存里组树 + 装标签 —— 比 N+1 次 JOIN 稳，也不怕 Room 的 Flow 组合开销。
 * - 写：都是单条 DAO 调用，不需要事务。
 *
 * ⚠️ 不依赖 [com.example.personalassistant.core.database.AppDatabase]：
 * 这样它能在 JVM 单测里用 Fake DAO 直接跑（`withTransaction` 需要真 Room 实例，跑不了）。
 *
 * ## Phase 16
 *
 * `sub_tasks` 表已并入 `tasks`，所以：
 * - 清单项本体就是 [Task]（`parentTaskId IS NULL` = 顶层项）
 * - 嵌套子项 = `parentTaskId = 父项 id` 的任务
 * - 标签关联统一走 [TaskTagDao]（`task_tag_cross_ref`），不再有 `sub_task_tag_cross_ref`
 */
@Singleton
class ChecklistRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val taskTagDao: TaskTagDao,
    private val tagDao: TagDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ChecklistRepository {

    override fun observeChecklistItems(): Flow<List<ChecklistItem>> =
        combine(
            // ⚠️ 要**含子项**的全量流：组树需要父子都在手边
            dao.observeAllIncludingChildren(),
            taskTagDao.observeAllTaskTagPairs(),
            tagDao.observeAll(),
        ) { rows, pairs, tags -> buildChecklistTree(rows, pairs, tags) }
            // 性能优化：buildChecklistTree 是 O(n) 的组树 + 装标签重活，
            // 原来跑在收集者的上下文里（UI 侧 = 主线程）→ 清单一多就会掉帧。
            // flowOn 把整条上游挪到 IO 线程；Room 查询仍走 Room 内部 executor，
            // 产出的数据逐字相同。
            .flowOn(dispatcher)

    override suspend fun getItem(id: Long): Task? = withContext(dispatcher) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun addItem(
        title: String,
        category: String?,
        dueDate: Long?,
        priority: TaskPriority,
        amount: Double?,
        listId: Long,
    ): Long = withContext(dispatcher) {
        // 追加到末尾（max + 1），修掉「order 恒为 0，新项全挤在最前面」的旧问题
        val nextOrder = (dao.getMaxTopLevelOrder() ?: -1L) + 1
        dao.insert(
            TaskEntity(
                parentTaskId = null,
                listId = listId,
                title = title,
                manualOrder = nextOrder,
                category = category,
                dueDate = dueDate,
                priority = priority.dbValue,
                amount = amount,
            ),
        )
    }

    override suspend fun addNestedItem(parentSubTaskId: Long, title: String): Long = withContext(dispatcher) {
        // 子项在**同一父项下**排序，所以取的是该父项下子项的最大 order
        val nextOrder = (dao.getMaxSubTaskOrder(parentSubTaskId) ?: -1L) + 1
        val parent = dao.getById(parentSubTaskId)
        dao.insert(
            TaskEntity(
                parentTaskId = parentSubTaskId,
                // 子项跟随父项的清单归属（Phase 17）
                listId = parent?.listId ?: TaskList.INBOX_ID,
                title = title,
                manualOrder = nextOrder,
            ),
        )
    }

    override suspend fun updateItem(item: Task) = withContext(dispatcher) {
        dao.update(item.toEntity())
    }

    override suspend fun deleteItem(item: Task) = withContext(dispatcher) {
        dao.delete(item.toEntity())
    }

    override suspend fun toggleComplete(id: Long, completed: Boolean) = withContext(dispatcher) {
        dao.setCompleted(id, completed, System.currentTimeMillis())
    }

    override suspend fun attachTag(subTaskId: Long, tagId: Long) = withContext(dispatcher) {
        taskTagDao.attach(TaskTagCrossRef(taskId = subTaskId, tagId = tagId))
    }

    override suspend fun detachTag(subTaskId: Long, tagId: Long) = withContext(dispatcher) {
        taskTagDao.detach(subTaskId, tagId)
    }
}

/**
 * 清单项嵌套的最大层级（顶层 + 子 + 孙）。
 *
 * 自引用 FK 理论上能形成环（A 的父是 B，B 的父是 A），
 * 所以组树时既限深度也防「祖先回环」。
 */
internal const val MAX_CHECKLIST_DEPTH = 3

/**
 * 把扁平的清单项行组装成「顶层项 + 标签 + 嵌套子清单」的树（纯函数，便于单测）。
 *
 * ## Phase 16
 *
 * 行全在 `tasks` 表里，层级靠 `parentTaskId` 表达：
 * - 顶层项 = `parentTaskId IS NULL`
 * - 子项 = `parentTaskId = 父项 id`
 *
 * @param rows 全部任务行（含子项）
 * @param pairs 任务 ↔ 标签 关联行
 * @param tags 全部标签
 */
internal fun buildChecklistTree(
    rows: List<TaskEntity>,
    pairs: List<TaskTagCrossRef>,
    tags: List<TagEntity>,
): List<ChecklistItem> {
    val tagsById = tags.associateBy { it.id }
    val tagIdsByTask = pairs.groupBy({ it.taskId }, { it.tagId })
    val domainRows = rows.map { it.toDomain() }
    val childrenByParent = domainRows
        .filter { it.parentTaskId != null }
        .groupBy { it.parentTaskId!! }
    return domainRows
        .filter { it.parentTaskId == null }
        .map { root ->
            buildChecklistItem(
                task = root,
                childrenByParent = childrenByParent,
                tagIdsByTask = tagIdsByTask,
                tagsById = tagsById,
                ancestors = setOf(root.id),
                depth = 0,
            )
        }
}

/** 递归组树（见 [buildChecklistTree]）。 */
private fun buildChecklistItem(
    task: Task,
    childrenByParent: Map<Long, List<Task>>,
    tagIdsByTask: Map<Long, List<Long>>,
    tagsById: Map<Long, TagEntity>,
    ancestors: Set<Long>,
    depth: Int,
): ChecklistItem {
    val tags = tagIdsByTask[task.id]
        .orEmpty()
        .mapNotNull { tagsById[it] }
        .map { it.toDomain() }
    // 防环：祖先里已经出现过的 id 不再往下展开
    val rawChildren = childrenByParent[task.id].orEmpty().filter { it.id !in ancestors }
    val children = if (depth < MAX_CHECKLIST_DEPTH) {
        rawChildren.map {
            buildChecklistItem(
                task = it,
                childrenByParent = childrenByParent,
                tagIdsByTask = tagIdsByTask,
                tagsById = tagsById,
                ancestors = ancestors + it.id,
                depth = depth + 1,
            )
        }
    } else {
        // 深度到顶：更深的子孙**拍平**挂到这一层，而不是静默丢掉 ——
        // 数据还在 DB 里，但界面上得看得见，否则用户以为东西没了。
        collectDescendants(task.id, childrenByParent, ancestors).map { descendant ->
            ChecklistItem(
                task = descendant,
                tags = tagIdsByTask[descendant.id]
                    .orEmpty()
                    .mapNotNull { tagsById[it] }
                    .map { it.toDomain() },
                children = emptyList(),
            )
        }
    }
    return ChecklistItem(task = task, tags = tags, children = children)
}

/**
 * 收集某一项的全部后代（深度不限），用于把超深子树拍平到当前层。
 *
 * @param visited 祖先链上的 id —— 出现过的直接跳过，防止环形引用把递归打爆
 */
private fun collectDescendants(
    parentId: Long,
    childrenByParent: Map<Long, List<Task>>,
    visited: Set<Long>,
): List<Task> {
    val result = mutableListOf<Task>()
    for (child in childrenByParent[parentId].orEmpty()) {
        if (child.id in visited) continue
        result += child
        result += collectDescendants(child.id, childrenByParent, visited + child.id)
    }
    return result
}
