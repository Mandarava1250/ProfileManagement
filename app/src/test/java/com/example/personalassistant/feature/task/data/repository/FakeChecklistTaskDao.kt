package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.data.local.SubTaskTagCrossRef
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 清单（sub_tasks）相关的内存版 [TaskDao]。
 *
 * 只实现清单这一摊的方法；任务（tasks）相关的方法一律 `error()` ——
 * 清单测试根本不该碰到它们，真碰到了说明代码写错了，比静默通过好。
 *
 * 内存语义尽量对齐真 Room：
 * - [observeStandaloneSubTasks] 只返回 `parentTaskId IS NULL` 的行（含嵌套子项），按 order / createdAt 升序
 * - [insertSubTask] 自动分配 id（autoGenerate），返回新行 id
 * - [attachSubTaskTag] 重复挂载静默忽略（对应 @Insert(onConflict = IGNORE) + 联合主键）
 */
class FakeChecklistTaskDao : TaskDao {

    private val subTasksState = MutableStateFlow<List<SubTaskEntity>>(emptyList())
    private val pairsState = MutableStateFlow<List<SubTaskTagCrossRef>>(emptyList())
    private var nextSubTaskId = 1L

    /** 当前存着的全部清单项（含嵌套子项）。 */
    val storedSubTasks: List<SubTaskEntity> get() = subTasksState.value

    /** 当前存着的全部「清单项 ↔ 标签」关联行。 */
    val storedTagPairs: List<SubTaskTagCrossRef> get() = pairsState.value

    /**
     * 直接塞一条清单项（模拟存量数据 / 构造测试场景）。
     *
     * @return 最终 id（传了 0 就自动分配）
     */
    fun seedSubTask(subTask: SubTaskEntity): Long {
        val id = if (subTask.id == 0L) nextSubTaskId++ else subTask.id
        // 显式塞了 id 的话，自增游标要跳过它 —— 否则后面 insertSubTask 会撞 id
        if (subTask.id != 0L && subTask.id >= nextSubTaskId) nextSubTaskId = subTask.id + 1
        subTasksState.value = subTasksState.value.filterNot { it.id == id } + subTask.copy(id = id)
        return id
    }

    // ===== sub_tasks（清单）相关：真实现 =====

    override suspend fun insertSubTask(subTask: SubTaskEntity): Long {
        val id = if (subTask.id == 0L) nextSubTaskId++ else subTask.id
        subTasksState.value = subTasksState.value.filterNot { it.id == id } + subTask.copy(id = id)
        return id
    }

    override suspend fun updateSubTask(subTask: SubTaskEntity) {
        subTasksState.value = subTasksState.value.map { if (it.id == subTask.id) subTask else it }
    }

    override suspend fun deleteSubTask(subTask: SubTaskEntity) {
        subTasksState.value = subTasksState.value.filterNot { it.id == subTask.id }
    }

    override suspend fun setSubTaskCompleted(id: Long, completed: Boolean) {
        subTasksState.value = subTasksState.value.map {
            if (it.id == id) it.copy(isCompleted = completed) else it
        }
    }

    override fun observeStandaloneSubTasks(): Flow<List<SubTaskEntity>> =
        subTasksState.map { rows -> rows.filter { it.parentTaskId == null }.sortedByOrder() }

    override fun observeTopLevelSubTasks(): Flow<List<SubTaskEntity>> =
        subTasksState.map { rows ->
            rows.filter { it.parentTaskId == null && it.parentSubTaskId == null }.sortedByOrder()
        }

    override fun observeNestedSubTasks(parentSubTaskId: Long): Flow<List<SubTaskEntity>> =
        subTasksState.map { rows ->
            rows.filter { it.parentSubTaskId == parentSubTaskId }.sortedByOrder()
        }

    override suspend fun getSubTaskById(id: Long): SubTaskEntity? =
        subTasksState.value.find { it.id == id }

    override suspend fun getMaxTopLevelSubTaskOrder(): Int? =
        subTasksState.value
            .filter { it.parentTaskId == null && it.parentSubTaskId == null }
            .maxOfOrNull { it.order }

    override suspend fun getMaxNestedSubTaskOrder(parentSubTaskId: Long): Int? =
        subTasksState.value
            .filter { it.parentSubTaskId == parentSubTaskId }
            .maxOfOrNull { it.order }

    override suspend fun attachSubTaskTag(ref: SubTaskTagCrossRef) {
        val exists = pairsState.value.any { it.subTaskId == ref.subTaskId && it.tagId == ref.tagId }
        if (!exists) {
            pairsState.value = pairsState.value + ref
        }
    }

    override suspend fun detachSubTaskTag(subTaskId: Long, tagId: Long) {
        pairsState.value = pairsState.value.filterNot { it.subTaskId == subTaskId && it.tagId == tagId }
    }

    override suspend fun detachAllSubTaskTags(subTaskId: Long) {
        pairsState.value = pairsState.value.filterNot { it.subTaskId == subTaskId }
    }

    override fun observeAllSubTaskTagPairs(): Flow<List<SubTaskTagCrossRef>> = pairsState

    override fun observeTopLevelSubTasksByCategory(category: String): Flow<List<SubTaskEntity>> =
        subTasksState.map { rows ->
            rows.filter {
                it.parentTaskId == null && it.parentSubTaskId == null && it.category == category
            }.sortedByOrder()
        }

    override fun observeTopLevelSubTasksByPriority(priority: Int): Flow<List<SubTaskEntity>> =
        subTasksState.map { rows ->
            rows.filter {
                it.parentTaskId == null && it.parentSubTaskId == null && it.priority == priority
            }.sortedByOrder()
        }

    override fun observeSubTaskIdsByTag(tagId: Long): Flow<List<Long>> =
        pairsState.map { pairs -> pairs.filter { it.tagId == tagId }.map { it.subTaskId } }

    override suspend fun getAllSubTaskTagCrossRefs(): List<SubTaskTagCrossRef> = pairsState.value

    override suspend fun getAllSubTasks(): List<SubTaskEntity> = subTasksState.value

    // ===== tasks（任务）相关：清单测试用不到 =====

    override suspend fun insert(task: TaskEntity): Long = notUsed("insert")

    override suspend fun update(task: TaskEntity): Unit = notUsed("update")

    override suspend fun delete(task: TaskEntity): Unit = notUsed("delete")

    override suspend fun deleteById(id: Long): Unit = notUsed("deleteById")

    override suspend fun getById(id: Long): TaskEntity? = notUsed("getById")

    override fun observeById(id: Long): Flow<TaskEntity?> = notUsed("observeById")

    override fun observeAll(): Flow<List<TaskEntity>> = notUsed("observeAll")

    override fun observeActive(): Flow<List<TaskEntity>> = notUsed("observeActive")

    override fun observeCompleted(): Flow<List<TaskEntity>> = notUsed("observeCompleted")

    override fun observeBetween(start: Long, end: Long): Flow<List<TaskEntity>> = notUsed("observeBetween")

    override fun observeBetweenIncludingCompleted(start: Long, end: Long): Flow<List<TaskEntity>> =
        notUsed("observeBetweenIncludingCompleted")

    override fun searchTasks(keyword: String): Flow<List<TaskEntity>> = notUsed("searchTasks")

    override suspend fun setCompleted(id: Long, completed: Boolean, now: Long): Unit =
        notUsed("setCompleted")

    override suspend fun updateManualOrder(id: Long, order: Long, now: Long): Unit =
        notUsed("updateManualOrder")

    override fun observeSubTasks(parentId: Long): Flow<List<SubTaskEntity>> = notUsed("observeSubTasks")

    override suspend fun getSubTasks(parentId: Long): List<SubTaskEntity> = notUsed("getSubTasks")

    override suspend fun getMaxSubTaskOrder(parentId: Long): Int? = notUsed("getMaxSubTaskOrder")

    override suspend fun deleteSubTasksByParent(parentId: Long): Unit =
        notUsed("deleteSubTasksByParent")

    override fun observeTagsForSubTask(subTaskId: Long): Flow<List<TagEntity>> =
        notUsed("observeTagsForSubTask")

    override suspend fun getAllTasks(): List<TaskEntity> = notUsed("getAllTasks")

    override suspend fun getTodayCompletionRate(todayStart: Long): Double? =
        notUsed("getTodayCompletionRate")

    override fun observeActiveCount(): Flow<Int> = notUsed("observeActiveCount")

    /** 清单测试里调用到任务相关 DAO 方法 = 代码写错了。 */
    private fun notUsed(name: String): Nothing =
        error("FakeChecklistTaskDao: $name 不该在清单测试里被调用")

    /** 对齐 Room 的 `ORDER BY order, createdAt`。 */
    private fun List<SubTaskEntity>.sortedByOrder(): List<SubTaskEntity> =
        sortedWith(compareBy({ it.order }, { it.createdAt }))
}
