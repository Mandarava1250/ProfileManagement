package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import com.example.personalassistant.feature.task.domain.repository.TagRepository
import com.example.personalassistant.feature.task.domain.repository.TaskDisplayRepository
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveAllTagsWithCountUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveTasksUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleTaskCompleteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 14：任务页要能看到已完成的任务
 *
 * 背景：任务页底部一直有个「已完成」折叠区，但展开是空的 ——
 * [TaskViewModel] 默认用 [FilterExpression.ActiveOnly] 去查，
 * 已完成的任务在数据流里根本不存在。
 *
 * 现在 ActiveOnly 只在**分组那一步**生效（active / completed 分开），
 * 数据层始终取全部任务 —— 所以这个测试锁住的是：
 * 「默认状态下 completed 分组非空」。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelCompletedTest {

    private val testDispatcher = StandardTestDispatcher()

    // ===== Fake TaskRepository =====

    /**
     * 模拟真仓库的筛选语义：
     * - [FilterExpression.ActiveOnly] → 只给未完成
     * - 其他 → 全部（含已完成）
     */
    private class FakeRepo(private val all: List<Task>) : TaskRepository {
        override fun observeActive() = MutableStateFlow(all.filter { !it.isCompleted })
        override fun observeCompleted() = MutableStateFlow(all.filter { it.isCompleted })
        override fun observeTaskById(id: Long) = MutableStateFlow<Task?>(null)
        override fun observeStandaloneChecklist() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeSubTasks(parentId: Long) = MutableStateFlow<List<Task>>(emptyList())
        override fun observeTasks(filter: TaskFilter, sort: TaskSort): MutableStateFlow<List<Task>> =
            MutableStateFlow(all)

        override fun observeTasks(
            filter: FilterExpression,
            sort: TaskSort,
        ): MutableStateFlow<List<Task>> = MutableStateFlow(
            if (filter is FilterExpression.ActiveOnly) all.filter { !it.isCompleted } else all
        )

        override suspend fun getTaskWithSubTasks(id: Long): TaskWithSubTasks? = null
        override suspend fun upsertTask(task: Task, subTasks: List<Task>): Long = -1L
        override suspend fun deleteTask(task: Task) {}
        override suspend fun toggleTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun toggleSubTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun addStandaloneSubTask(title: String): Long = -1L
        override suspend fun addSubTaskToTask(parentId: Long, title: String): Long = -1L
        override suspend fun deleteSubTask(subTask: Task) {}
        override suspend fun reorderTasks(orderedIds: List<Long>) {}
        override fun searchTasks(keyword: String) = MutableStateFlow<List<Task>>(emptyList())
    }

    // ===== Fake TagRepository =====

    private class FakeTagRepository : TagRepository {
        override fun observeAllTags() = MutableStateFlow<List<Tag>>(emptyList())
        override fun observeAllTagsWithCount() = MutableStateFlow<List<Tag>>(emptyList())
        override fun observeTagsForTask(taskId: Long) = MutableStateFlow<List<Tag>>(emptyList())
        override suspend fun createTag(displayName: String, colorHex: Long): Tag =
            Tag(id = 1L, nameNormalized = displayName.lowercase(), displayName = displayName, colorHex = colorHex)

        override suspend fun deleteTag(id: Long) {}
        override suspend fun addTagToTask(taskId: Long, tagId: Long) {}
        override suspend fun removeTagFromTask(taskId: Long, tagId: Long) {}
    }

    // ===== Fake TaskDisplayRepository =====

    private class FakeDisplayRepository : TaskDisplayRepository {
        private val state = MutableStateFlow(TaskDisplayPreferences())
        override fun observe() = state
        override suspend fun setAlias(quadrant: com.example.personalassistant.feature.task.domain.model.TaskQuadrant, alias: String) {}
        override suspend fun setViewMode(mode: com.example.personalassistant.feature.task.domain.model.TaskViewMode) {}
        override suspend fun selectQuadrant(quadrant: com.example.personalassistant.feature.task.domain.model.TaskQuadrant?) {}
    }

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun makeVm(all: List<Task>): TaskViewModel {
        val taskRepo = FakeRepo(all)
        val tagRepo = FakeTagRepository()
        val toggle = object : ToggleTaskCompleteUseCase(repository = taskRepo) {
            override suspend operator fun invoke(id: Long, completed: Boolean) {}
        }
        val delete = object : DeleteTaskUseCase(repository = taskRepo) {
            override suspend operator fun invoke(task: Task) {}
        }
        return TaskViewModel(
            observeTasks = ObserveTasksUseCase(taskRepo),
            observeAllTagsWithCount = ObserveAllTagsWithCountUseCase(tagRepo),
            toggleTaskComplete = toggle,
            deleteTask = delete,
            taskDisplayRepository = FakeDisplayRepository(),
        )
    }

    @Test
    fun `默认筛选下 已完成分组不为空`() = runTest {
        val vm = makeVm(
            listOf(
                Task(id = 1L, title = "未完成的任务", isCompleted = false),
                Task(id = 2L, title = "已完成的任务", isCompleted = true),
            )
        )
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(1, state.groups.completed.size)
        assertEquals("已完成的任务", state.groups.completed.first().title)
    }

    @Test
    fun `已完成任务不会挤进未完成的象限分组`() = runTest {
        val vm = makeVm(
            listOf(
                Task(id = 1L, title = "未完成的任务", isCompleted = false),
                Task(id = 2L, title = "已完成的任务", isCompleted = true),
            )
        )
        val state = vm.uiState.first { !it.isLoading }
        val activeCount = state.groups.byQuadrant.values.sumOf { it.size }
        assertEquals(1, activeCount)
        assertTrue(state.groups.completed.none { it.title == "未完成的任务" })
    }
}
