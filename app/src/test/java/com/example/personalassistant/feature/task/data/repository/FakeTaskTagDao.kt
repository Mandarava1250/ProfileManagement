package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 内存版 [TaskTagDao]（只服务于清单测试）。
 *
 * ## Phase 16
 *
 * 原来清单项的标签关联落在 `sub_task_tag_cross_ref`，由 `TaskDao` 上的
 * `attachSubTaskTag` 一类方法维护。现在这张表已并入 `task_tag_cross_ref`，
 * 关联读写统一走 [TaskTagDao]。
 *
 * 内存语义对齐真 Room：
 * - [attach] 重复挂载静默忽略（对应 `@Insert(onConflict = IGNORE)` + 联合主键）
 * - [observeAllTaskTagPairs] 返回全部关联行
 */
class FakeTaskTagDao : TaskTagDao {

    private val pairsState = MutableStateFlow<List<TaskTagCrossRef>>(emptyList())
    private val tagsState = MutableStateFlow<List<TagEntity>>(emptyList())

    /** 当前存着的全部「任务 ↔ 标签」关联行。 */
    val storedPairs: List<TaskTagCrossRef> get() = pairsState.value

    /** 塞一个标签实体（供 [observeTagsForTask] 做 INNER JOIN 用）。 */
    fun seedTag(tag: TagEntity) {
        tagsState.value = tagsState.value.filterNot { it.id == tag.id } + tag
    }

    override suspend fun attach(ref: TaskTagCrossRef) {
        val exists = pairsState.value.any { it.taskId == ref.taskId && it.tagId == ref.tagId }
        if (!exists) {
            pairsState.value = pairsState.value + ref
        }
    }

    override suspend fun detach(taskId: Long, tagId: Long) {
        pairsState.value = pairsState.value.filterNot { it.taskId == taskId && it.tagId == tagId }
    }

    override suspend fun detachAllForTask(taskId: Long) {
        pairsState.value = pairsState.value.filterNot { it.taskId == taskId }
    }

    override suspend fun detachAllForTag(tagId: Long) {
        pairsState.value = pairsState.value.filterNot { it.tagId == tagId }
    }

    /**
     * 与真 Room 同款：INNER JOIN 标签表，孤儿关联行拿不到标签。
     */
    override fun observeTagsForTask(taskId: Long): Flow<List<TagEntity>> =
        combine(pairsState, tagsState) { pairs, tags ->
            val tagsById = tags.associateBy { it.id }
            pairs.filter { it.taskId == taskId }
                .mapNotNull { tagsById[it.tagId] }
                .sortedBy { it.createdAt }
        }

    override fun observeTaskIdsByTag(tagId: Long): Flow<List<Long>> =
        pairsState.map { pairs -> pairs.filter { it.tagId == tagId }.map { it.taskId } }

    override fun observeAllTaskTagPairs(): Flow<List<TaskTagCrossRef>> = pairsState

    override suspend fun getAllCrossRefs(): List<TaskTagCrossRef> = pairsState.value

    override suspend fun attachAll(refs: List<TaskTagCrossRef>): List<Long> {
        val ids = mutableListOf<Long>()
        for (ref in refs) {
            val exists = pairsState.value.any { it.taskId == ref.taskId && it.tagId == ref.tagId }
            if (!exists) {
                pairsState.value = pairsState.value + ref
                ids += ref.taskId
            }
        }
        return ids
    }

    override suspend fun clearCrossRefs(): Int {
        val size = pairsState.value.size
        pairsState.value = emptyList()
        return size
    }
}
