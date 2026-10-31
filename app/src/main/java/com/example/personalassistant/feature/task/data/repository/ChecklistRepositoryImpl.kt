package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.data.local.SubTaskTagCrossRef
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.mapper.toDomain
import com.example.personalassistant.feature.task.data.mapper.toEntity
import com.example.personalassistant.feature.task.domain.model.ChecklistItem
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 15：清单仓库实现
 *
 * - 读：一次拿「全部独立清单项（含嵌套子项）+ 全部标签关联 + 全部标签」，
 *   在内存里组树 + 装标签 —— 比 N+1 次 JOIN 稳，也不怕 Room 的 Flow 组合开销。
 * - 写：都是单条 DAO 调用，不需要事务。
 *
 * ⚠️ 不依赖 [com.example.personalassistant.core.database.AppDatabase]：
 * 这样它能在 JVM 单测里用 Fake DAO 直接跑（`withTransaction` 需要真 Room 实例，跑不了）。
 */
@Singleton
class ChecklistRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val tagDao: TagDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ChecklistRepository {

    override fun observeChecklistItems(): Flow<List<ChecklistItem>> =
        combine(
            dao.observeStandaloneSubTasks(),
            dao.observeAllSubTaskTagPairs(),
            tagDao.observeAll(),
        ) { rows, pairs, tags -> buildChecklistTree(rows, pairs, tags) }

    override suspend fun getItem(id: Long): SubTask? = withContext(dispatcher) {
        dao.getSubTaskById(id)?.toDomain()
    }

    override suspend fun addItem(
        title: String,
        category: String?,
        dueDate: Long?,
        priority: TaskPriority,
        amount: Double?,
    ): Long = withContext(dispatcher) {
        // 追加到末尾（max + 1），修掉「order 恒为 0，新项全挤在最前面」的旧问题
        val nextOrder = (dao.getMaxTopLevelSubTaskOrder() ?: -1) + 1
        dao.insertSubTask(
            SubTaskEntity(
                parentTaskId = null,
                parentSubTaskId = null,
                title = title,
                order = nextOrder,
                category = category,
                dueDate = dueDate,
                priority = priority.dbValue,
                amount = amount,
            ),
        )
    }

    override suspend fun addNestedItem(parentSubTaskId: Long, title: String): Long = withContext(dispatcher) {
        // 子清单项在**同一父项下**排序，所以取的是该父项下子项的最大 order
        val nextOrder = (dao.getMaxNestedSubTaskOrder(parentSubTaskId) ?: -1) + 1
        dao.insertSubTask(
            SubTaskEntity(
                parentTaskId = null,
                parentSubTaskId = parentSubTaskId,
                title = title,
                order = nextOrder,
            ),
        )
    }

    override suspend fun updateItem(item: SubTask) = withContext(dispatcher) {
        dao.updateSubTask(item.toEntity())
    }

    override suspend fun deleteItem(item: SubTask) = withContext(dispatcher) {
        dao.deleteSubTask(item.toEntity())
    }

    override suspend fun toggleComplete(id: Long, completed: Boolean) = withContext(dispatcher) {
        dao.setSubTaskCompleted(id, completed)
    }

    override suspend fun attachTag(subTaskId: Long, tagId: Long) = withContext(dispatcher) {
        dao.attachSubTaskTag(SubTaskTagCrossRef(subTaskId = subTaskId, tagId = tagId))
    }

    override suspend fun detachTag(subTaskId: Long, tagId: Long) = withContext(dispatcher) {
        dao.detachSubTaskTag(subTaskId, tagId)
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
 * @param rows 全部独立清单项（含嵌套子项；父项用 parentSubTaskId 关联）
 * @param pairs 清单项 ↔ 标签 关联行
 * @param tags 全部标签
 */
internal fun buildChecklistTree(
    rows: List<SubTaskEntity>,
    pairs: List<SubTaskTagCrossRef>,
    tags: List<TagEntity>,
): List<ChecklistItem> {
    val tagsById = tags.associateBy { it.id }
    val tagIdsBySubTask = pairs.groupBy({ it.subTaskId }, { it.tagId })
    val domainRows = rows.map { it.toDomain() }
    val childrenByParent = domainRows
        .filter { it.parentSubTaskId != null }
        .groupBy { it.parentSubTaskId!! }
    return domainRows
        .filter { it.parentSubTaskId == null }
        .map { root ->
            buildChecklistItem(
                subTask = root,
                childrenByParent = childrenByParent,
                tagIdsBySubTask = tagIdsBySubTask,
                tagsById = tagsById,
                ancestors = setOf(root.id),
                depth = 0,
            )
        }
}

/** 递归组树（见 [buildChecklistTree]）。 */
private fun buildChecklistItem(
    subTask: SubTask,
    childrenByParent: Map<Long, List<SubTask>>,
    tagIdsBySubTask: Map<Long, List<Long>>,
    tagsById: Map<Long, TagEntity>,
    ancestors: Set<Long>,
    depth: Int,
): ChecklistItem {
    val tags = tagIdsBySubTask[subTask.id]
        .orEmpty()
        .mapNotNull { tagsById[it] }
        .map { it.toDomain() }
    val children = if (depth >= MAX_CHECKLIST_DEPTH) {
        emptyList()
    } else {
        childrenByParent[subTask.id]
            .orEmpty()
            // 防环：祖先里已经出现过的 id 不再往下展开
            .filter { it.id !in ancestors }
            .map {
                buildChecklistItem(
                    subTask = it,
                    childrenByParent = childrenByParent,
                    tagIdsBySubTask = tagIdsBySubTask,
                    tagsById = tagsById,
                    ancestors = ancestors + it.id,
                    depth = depth + 1,
                )
            }
    }
    return ChecklistItem(subTask = subTask, tags = tags, children = children)
}
