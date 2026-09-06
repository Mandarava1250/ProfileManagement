package com.example.personalassistant.feature.task.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import com.example.personalassistant.feature.task.domain.usecase.AddSubTaskToTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteSubTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveSubTasksUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleSubTaskCompleteUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleTaskCompleteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 6b：TaskDetailViewModel 单测。
 *
 * 沿用项目「Fake 优先」风格：所有 UseCase + Repository 手写 Fake，不引 Mockito。
 * 重点覆盖：
 * - addSubTask：空白标题不调 UseCase；正常标题调且清空 input
 * - deleteSubTask：调 DeleteSubTaskUseCase
 * - toggleSubTaskComplete：调 ToggleSubTaskCompleteUseCase
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ===== Fake Repository（接口签名仅满足 super 构造）=====

    private class FakeTaskRepository(
        val taskFlow: MutableStateFlow<Task?> = MutableStateFlow(null),
        val subTaskFlow: MutableStateFlow<List<SubTask>> = MutableStateFlow(emptyList()),
    ) : TaskRepository {
        override fun observeActive() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeCompleted() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeTaskById(id: Long) = taskFlow
        override fun observeStandaloneChecklist() = MutableStateFlow<List<SubTask>>(emptyList())
        override fun observeSubTasks(parentId: Long) = subTaskFlow
        override fun observeTasks(filter: TaskFilter, sort: TaskSort) =
            MutableStateFlow<List<Task>>(emptyList())

        override suspend fun getTaskWithSubTasks(id: Long): TaskWithSubTasks? = null
        override suspend fun upsertTask(task: Task, subTasks: List<SubTask>): Long = -1L
        override suspend fun deleteTask(task: Task) {}
        override suspend fun toggleTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun toggleSubTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun addStandaloneSubTask(title: String): Long = -1L
        override suspend fun addSubTaskToTask(parentId: Long, title: String): Long = -1L
        override suspend fun deleteSubTask(subTask: SubTask) {}
        override suspend fun reorderTasks(orderedIds: List<Long>) {}
    }

    // ===== Fake UseCases =====

    private class FakeObserveSubTasksUseCase(
        private val repo: FakeTaskRepository,
    ) : ObserveSubTasksUseCase(repository = repo) {
        override fun invoke(parentId: Long) = repo.subTaskFlow
    }

    private class FakeToggleTaskCompleteUseCase(
        private val repo: FakeTaskRepository,
    ) : ToggleTaskCompleteUseCase(repository = repo) {
        var calls: Int = 0; private set
        var lastId: Long = -1L; private set
        var lastCompleted: Boolean = false; private set
        override suspend operator fun invoke(id: Long, completed: Boolean) {
            calls++
            lastId = id
            lastCompleted = completed
        }
    }

    private class FakeToggleSubTaskCompleteUseCase(
        private val repo: FakeTaskRepository,
    ) : ToggleSubTaskCompleteUseCase(repository = repo) {
        var calls: Int = 0; private set
        var lastId: Long = -1L; private set
        var lastCompleted: Boolean = false; private set
        override suspend operator fun invoke(id: Long, completed: Boolean) {
            calls++
            lastId = id
            lastCompleted = completed
        }
    }

    private class FakeDeleteSubTaskUseCase(
        private val repo: FakeTaskRepository,
    ) : DeleteSubTaskUseCase(repository = repo) {
        var calls: Int = 0; private set
        var lastSubTask: SubTask? = null; private set
        override suspend operator fun invoke(subTask: SubTask) {
            calls++
            lastSubTask = subTask
        }
    }

    private class FakeAddSubTaskToTaskUseCase(
        private val repo: FakeTaskRepository,
    ) : AddSubTaskToTaskUseCase(repository = repo) {
        var calls: Int = 0; private set
        var lastParentId: Long = -1L; private set
        var lastTitle: String? = null; private set
        override suspend operator fun invoke(parentId: Long, title: String): Long {
            calls++
            lastParentId = parentId
            lastTitle = title
            return -1L
        }
    }

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun makeVm(taskId: Long = 100L): Pair<TaskDetailViewModel, FakeAddSubTaskToTaskUseCase> {
        val repo = FakeTaskRepository()
        val addSubTaskToTask = FakeAddSubTaskToTaskUseCase(repo)
        val vm = TaskDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(TaskDetailViewModel.ARG_TASK_ID to taskId)),
            observeSubTasks = FakeObserveSubTasksUseCase(repo),
            repository = repo,
            toggleTaskComplete = FakeToggleTaskCompleteUseCase(repo),
            toggleSubTaskCompleteUseCase = FakeToggleSubTaskCompleteUseCase(repo),
            deleteSubTaskUseCase = FakeDeleteSubTaskUseCase(repo),
            addSubTaskToTaskUseCase = addSubTaskToTask,
        )
        return vm to addSubTaskToTask
    }

    @Test
    fun `addSubTask 空白标题不调 UseCase`() = runTest {
        val (vm, addSubTask) = makeVm()
        // 输入纯空格
        vm.onNewSubTaskInputChange("   ")
        vm.addSubTask()
        advanceUntilIdle()

        // 关键断言：trim 后空 → VM 早返，不调 useCase
        assertEquals("空白标题不应该触发 UseCase", 0, addSubTask.calls)
    }

    @Test
    fun `addSubTask 正常标题调 UseCase 并清空 input`() = runTest {
        val (vm, addSubTask) = makeVm(taskId = 42L)
        vm.onNewSubTaskInputChange("买牛奶")
        vm.addSubTask()
        advanceUntilIdle()

        assertEquals(1, addSubTask.calls)
        assertEquals(42L, addSubTask.lastParentId)
        assertEquals("买牛奶", addSubTask.lastTitle)
        assertEquals("成功后 input 应清空", "", vm.uiState.value.newSubTaskInput)
    }

    @Test
    fun `addSubTask taskId 无效时不调 UseCase`() = runTest {
        // taskId <= 0L（无效）应该被 VM 拦截
        val (vm, addSubTask) = makeVm(taskId = -1L)
        vm.onNewSubTaskInputChange("hello")
        vm.addSubTask()
        advanceUntilIdle()

        assertEquals("无效 taskId 不应触发 UseCase", 0, addSubTask.calls)
    }

    @Test
    fun `toggleSubTaskComplete 转发到 UseCase`() = runTest {
        val repo = FakeTaskRepository()
        val toggleSubTask = FakeToggleSubTaskCompleteUseCase(repo)
        val vm = TaskDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(TaskDetailViewModel.ARG_TASK_ID to 1L)),
            observeSubTasks = FakeObserveSubTasksUseCase(repo),
            repository = repo,
            toggleTaskComplete = FakeToggleTaskCompleteUseCase(repo),
            toggleSubTaskCompleteUseCase = toggleSubTask,
            deleteSubTaskUseCase = FakeDeleteSubTaskUseCase(repo),
            addSubTaskToTaskUseCase = FakeAddSubTaskToTaskUseCase(repo),
        )

        vm.toggleSubTaskComplete(99L, true)
        advanceUntilIdle()

        assertEquals(1, toggleSubTask.calls)
        assertEquals(99L, toggleSubTask.lastId)
        assertTrue(toggleSubTask.lastCompleted)
    }

    @Test
    fun `deleteSubTask 转发到 UseCase（含子任务引用）`() = runTest {
        val repo = FakeTaskRepository()
        val deleteSubTask = FakeDeleteSubTaskUseCase(repo)
        val vm = TaskDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(TaskDetailViewModel.ARG_TASK_ID to 1L)),
            observeSubTasks = FakeObserveSubTasksUseCase(repo),
            repository = repo,
            toggleTaskComplete = FakeToggleTaskCompleteUseCase(repo),
            toggleSubTaskCompleteUseCase = FakeToggleSubTaskCompleteUseCase(repo),
            deleteSubTaskUseCase = deleteSubTask,
            addSubTaskToTaskUseCase = FakeAddSubTaskToTaskUseCase(repo),
        )
        val subTask = SubTask(id = 7L, parentTaskId = 1L, title = "test")

        vm.deleteSubTask(subTask)
        advanceUntilIdle()

        assertEquals(1, deleteSubTask.calls)
        assertNotNull(deleteSubTask.lastSubTask)
        assertEquals(7L, deleteSubTask.lastSubTask!!.id)
    }
}
