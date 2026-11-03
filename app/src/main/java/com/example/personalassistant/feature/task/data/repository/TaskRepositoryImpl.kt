package com.example.personalassistant.feature.task.data.repository

import androidx.room.withTransaction
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.task.data.local.DailyCompletionRow
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.task.data.mapper.toDomain
import com.example.personalassistant.feature.task.data.mapper.toEntity
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务仓库实现
 *
 * - 所有 IO 通过 [dispatcher]（IoDispatcher）执行
 * - upsertTask 走 withTransaction 保证原子性
 * - 子项按 FK SET NULL 行为：删除任务时子项自动转为顶层
 * - filter / sort 在内存层应用（Phase 4a）；未来可下沉到 DAO `@RawQuery`
 *
 * Phase 6c：
 * - 新增 [observeTasks] 的 [FilterExpression] 重载，支持 [FilterExpression.ByTag] + And/Or/Not 组合
 * - 旧 [observeTasks] ([TaskFilter], sort) 入口委托给新路径（通过 [TaskFilter.toExpression]）
 * - tag 筛选走内存过滤：从 [TaskTagDao.observeAllTaskTagPairs] 拉所有关联，构 tagId→taskIds 索引
 *
 * ## Phase 16
 *
 * `sub_tasks` 表已并入 `tasks`，所以这个仓库现在同时承担「任务」与「清单项」两种角色的读写 ——
 * 它们本来就是同一个模型。原先挂在 `subTaskDao` 上的子项 CRUD 全部改走 [dao]，
 * 「独立清单项」= 顶层任务（`parentTaskId IS NULL`）。
 */
@Singleton
/**
 * Phase 22.2：搜索结果里多个标签名的分隔符。
 *
 * 必须与 DAO 里 `GROUP_CONCAT(tg.displayName, ' / ')` 的分隔符一致 —— 否则切不开。
 */
private const val TAG_NAME_SEPARATOR = " / "

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

    /**
     * Phase 16：顶层任务流。
     *
     * 原语义是「独立清单项」（`sub_tasks` 里 `parentTaskId IS NULL` 的行）。
     * 合并后它们就是 `tasks` 表里 `parentTaskId IS NULL` 的行 —— 也就是 [TaskDao.observeAll]。
     */
    override fun observeStandaloneChecklist(): Flow<List<Task>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * Phase 6b：观察任务下的子项（详情页 SubTaskSection 用）
     */
    override fun observeSubTasks(parentId: Long): Flow<List<Task>> =
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
            // 性能优化：上面这坨「建 tag 索引 + 全量过滤 + 全量排序」是 O(n log n) 的重活，
            // 原来跑在收集者的上下文里（UI 侧 = 主线程）→ 任务一多就会掉帧。
            // flowOn 把整条上游（含 combine 的 transform）挪到 IO 线程，
            // Room 自己的查询仍走 Room 内部 executor，不受影响；产出的数据逐字相同。
            .flowOn(dispatcher)
    }

    // ===== 单点查询 =====

    override suspend fun getTaskWithSubTasks(id: Long): TaskWithSubTasks? =
        withContext(dispatcher) {
            val task = dao.getById(id)?.toDomain() ?: return@withContext null
            val subTasks = dao.getSubTasks(id).map { it.toDomain() }
            TaskWithSubTasks(task, subTasks)
        }

    /**
     * Phase 22.7：所有带提醒的未完成任务（含子任务）。
     *
     * 用 `dao.getAllTasks()`（不带 `parentTaskId IS NULL`）—— 子任务也能单独设提醒。
     */
    override suspend fun getTasksWithReminders(): List<Task> = withContext(dispatcher) {
        dao.getAllTasks()
            .asSequence()
            .filter { !it.isCompleted && it.reminderTime != null }
            .map { it.toDomain() }
            .toList()
    }

    /**
     * Phase 22.6：区间内按天的「计划数 / 完成数」。
     */
    override suspend fun getDailyCompletion(startMs: Long, endMs: Long): List<DailyCompletionRow> =
        withContext(dispatcher) { dao.getDailyCompletion(startMs, endMs) }

    /**
     * Phase 22.6：区间内按天的「计划数 / 完成数」（连续完成天数用宽窗口）。
     */
    override suspend fun getDailyCompletionUntil(startMs: Long, endMs: Long): List<DailyCompletionRow> =
        withContext(dispatcher) { dao.getDailyCompletionUntil(startMs, endMs) }

    // ===== 写操作 =====

    /**
     * 写入任务及其子项。
     *
     * Phase 16：子项不再是独立的 `SubTaskEntity`，而是同一张 `tasks` 表里的行，
     * 用 `parentTaskId` 指向父任务。
     */
    override suspend fun upsertTask(task: Task, subTasks: List<Task>): Long =
        withContext(dispatcher) {
            database.withTransaction {
                val taskId = dao.insert(task.toEntity())
                // 整体替换子项（子项 id 不保留；MVP 阶段可接受的简化）
                dao.deleteSubTasksByParent(taskId)
                subTasks.forEach { sub ->
                    dao.insert(sub.copy(parentTaskId = taskId).toEntity())
                }
                taskId
            }
        }

    override suspend fun deleteTask(task: Task) = withContext(dispatcher) {
        dao.delete(task.toEntity())
        // 子项由 FK SET NULL 自动转为顶层（设计意图：不删清单内容）
        // 任务↔标签关联由 FK CASCADE 自动清掉
    }

    override suspend fun toggleTaskComplete(id: Long, completed: Boolean) =
        withContext(dispatcher) {
            dao.setCompleted(id, completed, System.currentTimeMillis())
        }

    /**
     * Phase 16：子项与任务是同一张表，所以完成状态走同一个 [TaskDao.setCompleted]。
     */
    override suspend fun toggleSubTaskComplete(id: Long, completed: Boolean) =
        withContext(dispatcher) {
            dao.setCompleted(id, completed, System.currentTimeMillis())
        }

    /**
     * Phase 15/16：新增顶层项，manualOrder **追加到末尾**（max + 1）。
     *
     * 旧实现 order 恒为 0 → 所有独立清单项的 order 都是 0，
     * 新加的项会跟老项挤在一起（排序只能靠 createdAt 兜底，列表看着乱）。
     * 现在跟 [addSubTaskToTask] 同款做法：取当前最大 order + 1。
     */
    override suspend fun addStandaloneSubTask(title: String): Long =
        withContext(dispatcher) {
            val maxOrder = dao.getMaxTopLevelOrder() ?: -1L
            dao.insert(
                TaskEntity(
                    parentTaskId = null,
                    title = title,
                    manualOrder = maxOrder + 1,
                )
            )
        }

    /**
     * Phase 6b：在指定任务下新增一个子项，manualOrder 自动追加到末尾
     *
     * - 调用方（UseCase）保证 title 已 trim 非空
     * - parentId 对应 task 不存在时由 FK 报错（应不会发生，详情页 init 时已加载）
     */
    override suspend fun addSubTaskToTask(parentId: Long, title: String): Long =
        withContext(dispatcher) {
            val maxOrder = dao.getMaxSubTaskOrder(parentId) ?: -1L
            dao.insert(
                TaskEntity(
                    parentTaskId = parentId,
                    title = title,
                    manualOrder = maxOrder + 1,
                )
            )
        }

    override suspend fun deleteSubTask(subTask: Task) = withContext(dispatcher) {
        dao.delete(subTask.toEntity())
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

    /**
     * Phase 22.2：搜索命中来源。
     *
     * DAO 的搜索投影带两个「命中来源」字段：
     * - `matchedTagNames`：靠标签名命中的那些标签
     * - `matchedListName`：靠清单名命中的那个清单
     *
     * 两者填进 domain [Task] 的展示字段（[Task.listName] / [Task.tagNames]），
     * 全局搜索拿它显示「标签：Work」「清单：Work」。
     */
    override fun searchTasks(keyword: String): Flow<List<Task>> =
        dao.searchTasks(keyword).map { rows ->
            rows.map { row ->
                val task = row.task.toDomain()
                task.copy(
                    listName = row.matchedListName,
                    tagNames = row.matchedTagNames
                        ?.split(TAG_NAME_SEPARATOR)
                        ?.filter { it.isNotBlank() }
                        .orEmpty(),
                )
            }
        }

    // ===== filter 辅助 =====
    // Phase 6c：[FilterExpression] 的评估函数（matches / 等））
    // 已上提到 domain/model/FilterExpression.kt（internal 扩展函数），
    // 这里仅保留类型 import，复用同一份实现以保持 Repository 与测试用同一份逻辑。
}
