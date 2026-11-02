package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 清单（`tasks` 表）相关的内存版 [TaskDao]。
 *
 * ## Phase 16
 *
 * 原来这个 Fake 实现的是带 `sub_tasks` 分支的旧 TaskDao。现在 `sub_tasks` 表已并入 `tasks`，
 * 层级改用 `parentTaskId` 自引用表达：
 * - `parentTaskId IS NULL` = 顶层清单项
 * - `parentTaskId = 父项 id` = 嵌套子项
 *
 * 标签关联不再走这里 —— `sub_task_tag_cross_ref` 已并入 `task_tag_cross_ref`，
 * 由 [FakeTaskTagDao] 负责。
 *
 * 内存语义尽量对齐真 Room：
 * - [insert] 自动分配 id（autoGenerate），返回新行 id
 * - [observeAll] 只返回 `parentTaskId IS NULL` 的行，按 `manualOrder, createdAt` 升序
 * - [observeAllIncludingChildren] 返回全部行（父 + 子），同样按 `manualOrder, createdAt` 升序
 */
class FakeChecklistTaskDao : TaskDao {

    private val tasksState = MutableStateFlow<List<TaskEntity>>(emptyList())
    private var nextId = 1L

    /** 当前存着的全部任务行（含嵌套子项）。 */
    val storedTasks: List<TaskEntity> get() = tasksState.value

    /**
     * 直接塞一条任务行（模拟存量数据 / 构造测试场景）。
     *
     * @return 最终 id（传了 0 就自动分配）
     */
    fun seedTask(task: TaskEntity): Long {
        val id = if (task.id == 0L) nextId++ else task.id
        // 显式塞了 id 的话，自增游标要跳过它 —— 否则后面 insert 会撞 id
        if (task.id != 0L && task.id >= nextId) nextId = task.id + 1
        tasksState.value = tasksState.value.filterNot { it.id == id } + task.copy(id = id)
        return id
    }

    // ===== 写操作 =====

    override suspend fun insert(task: TaskEntity): Long {
        val id = if (task.id == 0L) nextId++ else task.id
        if (task.id != 0L && task.id >= nextId) nextId = task.id + 1
        tasksState.value = tasksState.value.filterNot { it.id == id } + task.copy(id = id)
        return id
    }

    override suspend fun update(task: TaskEntity) {
        tasksState.value = tasksState.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun delete(task: TaskEntity) {
        tasksState.value = tasksState.value.filterNot { it.id == task.id }
    }

    override suspend fun deleteById(id: Long) {
        tasksState.value = tasksState.value.filterNot { it.id == id }
    }

    override suspend fun setCompleted(id: Long, completed: Boolean, now: Long) {
        tasksState.value = tasksState.value.map {
            if (it.id == id) it.copy(isCompleted = completed, updatedAt = now) else it
        }
    }

    override suspend fun updateManualOrder(id: Long, order: Long, now: Long) {
        tasksState.value = tasksState.value.map {
            if (it.id == id) it.copy(manualOrder = order, updatedAt = now) else it
        }
    }

    override suspend fun deleteSubTasksByParent(parentId: Long) {
        tasksState.value = tasksState.value.filterNot { it.parentTaskId == parentId }
    }

    override suspend fun deleteTaskWithChildren(task: TaskEntity) {
        delete(task)
        deleteSubTasksByParent(task.id)
    }

    // ===== 单点查询 =====

    override suspend fun getById(id: Long): TaskEntity? =
        tasksState.value.find { it.id == id }

    override fun observeById(id: Long): Flow<TaskEntity?> =
        tasksState.map { rows -> rows.find { it.id == id } }

    // ===== 观察流 =====

    /** 顶层清单项（`parentTaskId IS NULL`），按 `manualOrder, createdAt` 升序。 */
    override fun observeAll(): Flow<List<TaskEntity>> =
        tasksState.map { rows -> rows.filter { it.parentTaskId == null }.sortedByManualOrder() }

    /** 全部任务行（父 + 子），按 `manualOrder, createdAt` 升序。 */
    override fun observeAllIncludingChildren(): Flow<List<TaskEntity>> =
        tasksState.map { rows -> rows.sortedByManualOrder() }

    /** 某父项下的子项，按 `manualOrder, createdAt` 升序。 */
    override fun observeSubTasks(parentId: Long): Flow<List<TaskEntity>> =
        tasksState.map { rows -> rows.filter { it.parentTaskId == parentId }.sortedByManualOrder() }

    override suspend fun getSubTasks(parentId: Long): List<TaskEntity> =
        tasksState.value.filter { it.parentTaskId == parentId }.sortedByManualOrder()

    override fun observeTopLevelByCategory(category: String): Flow<List<TaskEntity>> =
        tasksState.map { rows ->
            rows.filter { it.parentTaskId == null && it.category == category }.sortedByManualOrder()
        }

    override fun observeTopLevelByPriority(priority: Int): Flow<List<TaskEntity>> =
        tasksState.map { rows ->
            rows.filter { it.parentTaskId == null && it.priority == priority }.sortedByManualOrder()
        }

    override fun observeActive(): Flow<List<TaskEntity>> =
        tasksState.map { rows -> rows.filter { it.parentTaskId == null && !it.isCompleted } }

    override fun observeCompleted(): Flow<List<TaskEntity>> =
        tasksState.map { rows -> rows.filter { it.parentTaskId == null && it.isCompleted } }

    override fun observeActiveCount(): Flow<Int> =
        tasksState.map { rows -> rows.count { it.parentTaskId == null && !it.isCompleted } }

    // ===== 排序权重 =====

    override suspend fun getMaxTopLevelOrder(): Long? =
        tasksState.value
            .filter { it.parentTaskId == null }
            .mapNotNull { it.manualOrder }
            .maxOrNull()

    override suspend fun getMaxSubTaskOrder(parentId: Long): Long? =
        tasksState.value
            .filter { it.parentTaskId == parentId }
            .mapNotNull { it.manualOrder }
            .maxOrNull()

    // ===== 备份导入用的批量操作 =====

    /**
     * 批量插入（REPLACE，与 [insert] 一致）。
     *
     * ⚠️ 逐字复刻 [insert] 的 id 分配逻辑，**不**额外推进自增游标 ——
     * 批量与逐条两条路径必须给出完全一样的 id。
     */
    override suspend fun insertTasks(tasks: List<TaskEntity>): List<Long> {
        val ids = mutableListOf<Long>()
        tasksState.value = tasksState.value + tasks.map { task ->
            val id = if (task.id == 0L) nextId++ else task.id
            ids += id
            task.copy(id = id)
        }
        return ids
    }

    override suspend fun clearTasks(): Int {
        val size = tasksState.value.size
        tasksState.value = emptyList()
        return size
    }

    override suspend fun getAllTasks(): List<TaskEntity> = tasksState.value

    // ===== 清单测试用不到的方法 =====

    override fun observeBetween(start: Long, end: Long): Flow<List<TaskEntity>> = notUsed("observeBetween")

    override fun observeBetweenIncludingCompleted(start: Long, end: Long): Flow<List<TaskEntity>> =
        notUsed("observeBetweenIncludingCompleted")

    override fun searchTasks(keyword: String): Flow<List<TaskEntity>> = notUsed("searchTasks")

    override suspend fun getTodayCompletionRate(todayStart: Long): Double? =
        notUsed("getTodayCompletionRate")

    /** 清单测试里调用到任务列表相关 DAO 方法 = 代码写错了。 */
    private fun notUsed(name: String): Nothing =
        error("FakeChecklistTaskDao: $name 不该在清单测试里被调用")

    /** 对齐 Room 的 `ORDER BY manualOrder, createdAt`。 */
    private fun List<TaskEntity>.sortedByManualOrder(): List<TaskEntity> =
        sortedWith(compareBy({ it.manualOrder }, { it.createdAt }))
}
