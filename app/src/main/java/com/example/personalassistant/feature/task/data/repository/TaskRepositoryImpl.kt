package com.example.personalassistant.feature.task.data.repository

import androidx.room.withTransaction
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.task.data.mapper.toDomain
import com.example.personalassistant.feature.task.data.mapper.toEntity
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import com.example.personalassistant.feature.task.domain.model.toComparator
import com.example.personalassistant.feature.task.domain.model.toExpression
import com.example.personalassistant.feature.task.domain.model.matches
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import com.example.personalassistant.feature.task.domain.usecase.ObserveTasksByQuadrantUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 * - filter / sort 在内存层应用（Phase 4a）；未来可下沉到 DAO `@RawQuery`
 *
 * Phase 6c：
 * - 新增 [observeTasks] 的 [FilterExpression] 重载，支持 [FilterExpression.ByTag] + And/Or/Not 组合
 * - 旧 [observeTasks] ([TaskFilter], sort) 入口委托给新路径（通过 [TaskFilter.toExpression]）
 * - tag 筛选走内存过滤：从 [TaskTagDao.observeAllTaskTagPairs] 拉所有关联，构 tagId→taskIds 索引
 */
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val taskTagDao: TaskTagDao,
    private val database: AppDatabase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : TaskRepository {

    // ===== 观察流 =====

    override fun observeActive(): Flow<List<Task>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeCompleted(): Flow<List<Task>> =
        dao.observeCompleted().map { list -> list.map { it.toDomain() } }

    /**
     * Phase 6b：详情页 task 实时刷新用
     */
    override fun observeTaskById(id: Long): Flow<Task?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun observeStandaloneChecklist(): Flow<List<SubTask>> =
        dao.observeStandaloneSubTasks().map { list -> list.map { it.toDomain() } }

    /**
     * Phase 6b：观察任务下的子清单（详情页 SubTaskSection 用）
     */
    override fun observeSubTasks(parentId: Long): Flow<List<SubTask>> =
        dao.observeSubTasks(parentId).map { list -> list.map { it.toDomain() } }

    /**
     * 旧路径：接 [TaskFilter] 的入口（向后兼容）。
     *
     * 内部直接委托给 [observeTasks] ([FilterExpression], sort) 重载。
     */
    override fun observeTasks(
        filter: TaskFilter,
        sort: TaskSort,
    ): Flow<List<Task>> = observeTasks(filter.toExpression(), sort)

    /**
     * 新路径：接 [FilterExpression] 的入口。
     *
     * 实现要点：
     * 1. 决定源流：filter 不含 ByTag 时走 [TaskDao.observeAll]（拿全量任务，内存层 filter）
     *    - Phase 4a 优化策略：若 filter 是 ActiveOnly / CompletedOnly，走对应索引流
     * 2. 内存层评估 [FilterExpression] 树（递归 matches）
     * 3. sort 一律内存层做
     *
     * Phase 6c：ByTag 路径需要 [TaskTagDao.observeAllTaskTagPairs] 提供的关联索引，
     * 通过 [combine] 拉到内存里建 tagId → Set<taskId> 映射。
     */
    override fun observeTasks(
        filter: FilterExpression,
        sort: TaskSort,
    ): Flow<List<Task>> {
        val todayEnd = ObserveTasksByQuadrantUseCase.todayEndMillis()
        // 含 ByTag 时拉关联表，构索引
        val sourceFlow: Flow<List<Task>> = when (filter) {
            FilterExpression.ActiveOnly -> dao.observeActive().map { it.map { e -> e.toDomain() } }
            FilterExpression.CompletedOnly -> dao.observeCompleted().map { it.map { e -> e.toDomain() } }
            else -> dao.observeAll().map { it.map { e -> e.toDomain() } }
        }
        return combine(sourceFlow, taskTagDao.observeAllTaskTagPairs()) { tasks, pairs ->
            val tagIndex: Map<Long, Set<Long>> = pairs.groupBy(
                keySelector = { it.tagId },
                valueTransform = { it.taskId }
            ).mapValues { it.value.toSet() }
            tasks.asSequence()
                .filter { task -> filter.matches(task, todayEnd, tagIndex) }
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
        // 任务↔标签关联由 FK CASCADE 自动清掉
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

    /**
     * Phase 6b：在指定任务下新增一个子清单项，order 自动追加到末尾
     *
     * - 调用方（UseCase）保证 title 已 trim 非空
     * - parentId 对应 task 不存在时由 FK 报错（应不会发生，详情页 init 时已加载）
     */
    override suspend fun addSubTaskToTask(parentId: Long, title: String): Long =
        withContext(dispatcher) {
            val maxOrder = dao.getMaxSubTaskOrder(parentId) ?: -1
            dao.insertSubTask(
                SubTaskEntity(
                    parentTaskId = parentId,
                    title = title,
                    order = maxOrder + 1,
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

    // ===== Phase 8 Sub-step 3 全局搜索 =====

    override fun searchTasks(keyword: String): Flow<List<Task>> =
        dao.searchTasks(keyword).map { list -> list.map { it.toDomain() } }

    // ===== filter 辅助 =====
    // Phase 6c：[FilterExpression] 的评估函数（matches / 等））
    // 已上提到 domain/model/FilterExpression.kt（internal 扩展函数），
    // 这里仅保留类型 import，复用同一份实现以保持 Repository 与测试用同一份逻辑。
}