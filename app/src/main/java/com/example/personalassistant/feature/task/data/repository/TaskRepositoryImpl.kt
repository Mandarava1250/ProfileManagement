package com.example.personalassistant.feature.task.data.repository

import androidx.room.withTransaction
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.mapper.toDomain
import com.example.personalassistant.feature.task.data.mapper.toEntity
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import com.example.personalassistant.feature.task.domain.model.toComparator
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务仓库实现
 *
 * - 所有 IO 通过 [dispatcher]（IoDispatcher）执行
 * - upsertTask 走 withTransaction 保证原子性
 * - 子清单按 FK SET_NULL 行为：删除任务时子清单自动转为独立项
 * - filter / sort 在内存层应用（Phase 4a）；未来可下沉到 DAO @RawQuery
 */
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val database: AppDatabase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : TaskRepository {

    // ===== 观察流 =====

    override fun observeActive(): Flow<List<Task>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeCompleted(): Flow<List<Task>> =
        dao.observeCompleted().map { list -> list.map { it.toDomain() } }

    override fun observeStandaloneChecklist(): Flow<List<SubTask>> =
        dao.observeStandaloneSubTasks().map { list -> list.map { it.toDomain() } }

    /**
     * 通用观察入口：filter + sort。
     *
     * Phase 4a 简化策略：
     * - filter 是 "ActiveOnly" / "CompletedOnly" 时直接走对应的 DAO 索引流（性能更好）
     * - filter 是 "All" / "ByCategory" / "ByQuadrant" 时，从 observeAll 拉全量然后内存过滤
     * - sort 一律在内存层做
     *
     * Phase 6a：[sort.toComparator()] 已上提到 [TaskSort.kt] 同包顶层（internal），
     * 同包测试可直接复用，避免双份维护。
     */
    override fun observeTasks(
        filter: TaskFilter,
        sort: TaskSort,
    ): Flow<List<Task>> {
        val sourceFlow: Flow<List<Task>> = when (filter) {
            TaskFilter.ActiveOnly -> dao.observeActive().map { it.map { e -> e.toDomain() } }
            TaskFilter.CompletedOnly -> dao.observeCompleted().map { it.map { e -> e.toDomain() } }
            else -> dao.observeAll().map { it.map { e -> e.toDomain() } }
        }
        return sourceFlow.map { tasks ->
            tasks
                .asSequence()
                .filter { task -> filter.matches(task) }
                .toList()
                .sortedWith(sort.toComparator())
        }
    }

    // ===== 单点查询 =====

    override suspend fun getTaskWithSubTasks(id: Long): TaskWithSubTasks? =
        withContext(dispatcher) {
            val task = dao.getById(id)?.toDomain() ?: return@withContext null
            val subTasks = dao.getSubTasks(id).map { it.toDomain() }
            TaskWithSubTasks(task, subTasks)
        }

    // ===== 写操作 =====

    override suspend fun upsertTask(task: Task, subTasks: List<SubTask>): Long =
        withContext(dispatcher) {
            database.withTransaction {
                val taskId = dao.insert(task.toEntity())
                // 整体替换子清单（清单项 id 不保留；MVP 阶段可接受的简化）
                dao.deleteSubTasksByParent(taskId)
                subTasks.forEach { sub ->
                    dao.insertSubTask(sub.copy(parentTaskId = taskId).toEntity())
                }
                taskId
            }
        }

    override suspend fun deleteTask(task: Task) = withContext(dispatcher) {
        dao.delete(task.toEntity())
        // 子清单由 FK SET_NULL 自动转为独立项（设计意图：不删清单内容）
    }

    override suspend fun toggleTaskComplete(id: Long, completed: Boolean) =
        withContext(dispatcher) {
            dao.setCompleted(id, completed, System.currentTimeMillis())
        }

    override suspend fun toggleSubTaskComplete(id: Long, completed: Boolean) =
        withContext(dispatcher) {
            dao.setSubTaskCompleted(id, completed)
        }

    override suspend fun addStandaloneSubTask(title: String): Long =
        withContext(dispatcher) {
            dao.insertSubTask(
                SubTaskEntity(
                    parentTaskId = null,
                    title = title,
                )
            )
        }

    override suspend fun deleteSubTask(subTask: SubTask) = withContext(dispatcher) {
        dao.deleteSubTask(subTask.toEntity())
    }

    /**
     * Phase 6a：批量重排任务 manualOrder（拖拽结束时一次性提交）。
     *
     * - [orderedIds] 的顺序即落库后的 manualOrder 升序
     * - 整体在一个事务里执行，要么全部成功要么全部回滚
     * - 不在列表里的任务 manualOrder 保持不变（兜底按 id 升序）
     */
    override suspend fun reorderTasks(orderedIds: List<Long>) = withContext(dispatcher) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            orderedIds.forEachIndexed { index, id ->
                dao.updateManualOrder(id = id, order = index.toLong(), now = now)
            }
        }
    }

    // ===== filter 辅助 =====

    /**
     * TaskFilter 的纯函数匹配（已经走过"ActiveOnly/CompletedOnly → 走对应 DAO 流"那一层，
     * 这里主要处理 "All / ByCategory / ByQuadrant"）。
     */
    private fun TaskFilter.matches(task: Task): Boolean = when (this) {
        TaskFilter.All, TaskFilter.ActiveOnly, TaskFilter.CompletedOnly -> true
        is TaskFilter.ByCategory -> task.category.equals(categoryName, ignoreCase = true)
        is TaskFilter.ByQuadrant -> {
            // 委托给 ObserveTasksByQuadrantUseCase.classifyOne（已有公开 companion 方法）
            val todayEnd = com.example.personalassistant.feature.task.domain.usecase
                .ObserveTasksByQuadrantUseCase.todayEndMillis()
            com.example.personalassistant.feature.task.domain.usecase
                .ObserveTasksByQuadrantUseCase.classifyOne(task, todayEnd) == quadrant
        }
    }

    private val TaskPriority.rank: Int get() = when (this) {
        TaskPriority.HIGH -> 2
        TaskPriority.MEDIUM -> 1
        TaskPriority.LOW -> 0
    }
}
