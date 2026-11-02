package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Phase 8 Sub-step 3：单元测试用的最小 [TaskRepository] stub
 *
 * 只实现 [searchTasks] + 必要的 [observeActive] 兜底。其他方法全部抛 [NotImplementedError]，
 * 因为 Sub-step 3 测试只需要 search 路径 + 一个触发响应式的源。
 */
class FakeTaskRepository : TaskRepository {

    private val tasksState = MutableStateFlow<List<Task>>(emptyList())

    /** 调试用：观察 upsertTask 次数 */
    var upsertCount: Int = 0
        private set

    // ===== 读流（最小实现）=====

    override fun observeActive(): Flow<List<Task>> =
        tasksState.map { list -> list.filter { !it.isCompleted } }

    override fun observeCompleted(): Flow<List<Task>> =
        tasksState.map { list -> list.filter { it.isCompleted } }

    override fun observeTaskById(id: Long): Flow<Task?> =
        tasksState.map { list -> list.firstOrNull { it.id == id } }

    override fun observeStandaloneChecklist(): Flow<List<Task>> =
        MutableStateFlow<List<Task>>(emptyList())

    override fun observeSubTasks(parentId: Long): Flow<List<Task>> =
        MutableStateFlow<List<Task>>(emptyList())

    override fun observeTasks(
        filter: TaskFilter,
        sort: TaskSort,
    ): Flow<List<Task>> = observeActive()

    override fun observeTasks(
        filter: FilterExpression,
        sort: TaskSort,
    ): Flow<List<Task>> = observeActive()

    override suspend fun getTaskWithSubTasks(id: Long): TaskWithSubTasks? = null

    // ===== 写操作 =====

    override suspend fun upsertTask(task: Task, subTasks: List<Task>): Long {
        upsertCount++
        val resolved = if (task.id > 0L) task else task.copy(id = (tasksState.value.size + 1).toLong())
        tasksState.value = tasksState.value + resolved
        return resolved.id
    }

    override suspend fun deleteTask(task: Task) {
        tasksState.value = tasksState.value.filter { it.id != task.id }
    }

    override suspend fun toggleTaskComplete(id: Long, completed: Boolean) {
        tasksState.value = tasksState.value.map {
            if (it.id == id) it.copy(isCompleted = completed) else it
        }
    }

    override suspend fun toggleSubTaskComplete(id: Long, completed: Boolean) = Unit
    override suspend fun addStandaloneSubTask(title: String): Long = 0L
    override suspend fun addSubTaskToTask(parentId: Long, title: String): Long = 0L
    override suspend fun deleteSubTask(subTask: Task) = Unit
    override suspend fun reorderTasks(orderedIds: List<Long>) = Unit

    // ===== Phase 8 Sub-step 3 全局搜索 =====

    /**
     * 测试用搜索实现：
     * - :keyword 形如 `%xxx%`（包装好通配符）
     * - 去掉首尾 `%` 后在 title / description / category 任一字段子串匹配
     */
    override fun searchTasks(keyword: String): Flow<List<Task>> =
        tasksState.map { list ->
            val needle = keyword.removePrefix("%").removeSuffix("%")
            list.filter { task ->
                task.title.contains(needle, ignoreCase = false) ||
                    (task.description?.contains(needle, ignoreCase = false) == true) ||
                    (task.category?.contains(needle, ignoreCase = false) == true)
            }
        }

    // ===== 测试辅助 helpers =====

    /** 直接注入一条任务（带 id） */
    suspend fun seedTask(task: Task) {
        tasksState.value = tasksState.value + task
    }

    fun clear() {
        tasksState.value = emptyList()
    }
}
