package com.example.personalassistant.feature.task.domain.repository

import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import kotlinx.coroutines.flow.Flow

/**
 * 任务领域仓库接口
 *
 * 由 Data 层的 TaskRepositoryImpl 实现。
 */
interface TaskRepository {

    // ===== 观察流 =====

    /** 未完成任务（按 priority DESC, dueDate ASC） */
    fun observeActive(): Flow<List<Task>>

    /** 已完成任务（按 dueDate DESC） */
    fun observeCompleted(): Flow<List<Task>>

    /**
     * Phase 6b：观察单个任务（详情页用，id 不存在时 emit null）
     *
     * - 任务被删除时 emit null，UI 监听触发 popBackStack
     * - 任务被更新（标题/优先级/截止/...）时自动刷新 UI
     */
    fun observeTaskById(id: Long): Flow<Task?>

    /** 独立清单项（parentTaskId IS NULL） */
    fun observeStandaloneChecklist(): Flow<List<SubTask>>

    /**
     * Phase 6b：观察某个任务下的子清单（parentTaskId = taskId）
     *
     * - 用于详情页子任务 section 实时刷新
     * - 排序：`order` ASC, `createdAt` ASC（与 DAO 查询一致）
     */
    fun observeSubTasks(parentId: Long): Flow<List<SubTask>>

    /**
     * 旧路径：通用观察入口 —— 接 [TaskFilter] 和 [TaskSort]，返回过滤+排序后的任务流。
     *
     * Phase 4a：filter 在内存里应用（适合 MVP 数据量），sort 也走内存 .sortedWith()。
     * Phase 4b：可下沉到 DAO `@RawQuery`。
     *
     * Phase 6c：内部委托给 [observeTasks(filter: FilterExpression, sort)]（通过
     * [TaskFilter.toExpression] 转译），保持 API 向后兼容。
     */
    fun observeTasks(filter: TaskFilter = TaskFilter.ActiveOnly, sort: TaskSort = TaskSort.DEFAULT): Flow<List<Task>>

    /**
     * Phase 6c 新增路径：通用观察入口 —— 接 [FilterExpression] 和 [TaskSort]。
     *
     * 支持：
     * - 旧 case（All / ByCategory / ByQuadrant / ActiveOnly / CompletedOnly）行为不变
     * - 新增 [FilterExpression.ByTag] 单 tag 筛选
     * - 复合 [FilterExpression.And] / [FilterExpression.Or] / [FilterExpression.Not] 树组合
     *
     * 列表页（TasksTab）新代码路径会走这里；旧 UI 代码（仍用 TaskFilter）的路径
     * 内部通过 [TaskFilter.toExpression] 委托到此方法，避免重复逻辑。
     */
    fun observeTasks(filter: FilterExpression, sort: TaskSort = TaskSort.DEFAULT): Flow<List<Task>>

    // ===== 单点查询 =====

    /** 获取任务与子清单；id 不存在时返回 null */
    suspend fun getTaskWithSubTasks(id: Long): TaskWithSubTasks?

    // ===== 写操作 =====

    /**
     * 写入任务及其子清单。
     *
     * - 若 task.id = 0L 视为新增，返回新生成的 rowId
     * - 若 task.id > 0L 视为更新；同时把 subTasks 列表整体替换为任务下的清单
     * - 整个操作在一个事务内完成
     */
    suspend fun upsertTask(task: Task, subTasks: List<SubTask>): Long

    /** 删除任务。其子清单由 FK SET_NULL 自动转为独立项（不删除清单内容）。 */
    suspend fun deleteTask(task: Task)

    /** 切换任务完成状态 */
    suspend fun toggleTaskComplete(id: Long, completed: Boolean)

    /** 切换清单项完成状态 */
    suspend fun toggleSubTaskComplete(id: Long, completed: Boolean)

    /** 添加独立清单项（parentTaskId = null） */
    suspend fun addStandaloneSubTask(title: String): Long

    /**
     * Phase 6b：在指定任务下新增一个子清单项（parentTaskId = parentId）
     *
     * - 自动追加到当前最大 `order` + 1，避免显示错乱
     * - 空白标题由 UseCase 层拦截校验
     * - parentId 对应的 task 不存在时由 FK 报错（应不会发生，由 ViewModel 在 init 阶段保证）
     */
    suspend fun addSubTaskToTask(parentId: Long, title: String): Long

    /** 删除清单项 */
    suspend fun deleteSubTask(subTask: SubTask)

    /**
     * Phase 6a：批量重排任务 manualOrder（拖拽结束时一次性提交）。
     *
     * - [orderedIds] 的顺序即落库后的 manualOrder 升序
     * - 整体在一个事务里执行
     * - 不在列表里的任务 manualOrder 保持不变
     */
    suspend fun reorderTasks(orderedIds: List<Long>)
}