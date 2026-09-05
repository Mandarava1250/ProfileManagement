package com.example.personalassistant.feature.task.presentation.edit

import androidx.lifecycle.SavedStateHandle
import com.example.personalassistant.core.notification.FakeNotificationPermissionRequester
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import com.example.personalassistant.feature.task.domain.usecase.CancelReminderUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.GetTaskWithSubTasksUseCase
import com.example.personalassistant.feature.task.domain.usecase.ScheduleReminderUseCase
import com.example.personalassistant.feature.task.domain.usecase.UpsertTaskUseCase
import com.example.personalassistant.navigation.Screen
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 5b: TaskEditViewModel 通知权限接线单测。
 *
 * 不引 Mockito —— 沿用项目「Fake 优先」风格：所有 UseCase 手写 Fake。
 * 重点覆盖 save() 的三路分支 + onPermissionResult 的两个方向 + consume 一次性信号。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ===== No-op 接口实现（仅满足 super 构造；Fake 子类会 override invoke 不走 super）=====

    private object NoopTaskRepository : TaskRepository {
        override fun observeActive() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeCompleted() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeStandaloneChecklist() = MutableStateFlow<List<SubTask>>(emptyList())
        override fun observeTasks(filter: TaskFilter, sort: TaskSort) =
            MutableStateFlow<List<Task>>(emptyList())
        override suspend fun getTaskWithSubTasks(id: Long): TaskWithSubTasks? = null
        override suspend fun upsertTask(task: Task, subTasks: List<SubTask>): Long = -1L
        override suspend fun deleteTask(task: Task) {}
        override suspend fun toggleTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun toggleSubTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun addStandaloneSubTask(title: String): Long = -1L
        override suspend fun deleteSubTask(subTask: SubTask) {}
        override suspend fun reorderTasks(orderedIds: List<Long>) {}
    }

    private object NoopReminderScheduler : ReminderScheduler {
        override suspend fun schedule(info: ReminderInfo) {}
        override suspend fun cancel(taskId: Long) {}
        override suspend fun cancelAll() {}
    }

    // ===== Fake UseCase（手写项目风格）=====

    private class FakeGetTaskWithSubTasksUseCase(
        var result: TaskWithSubTasks? = null,
    ) : GetTaskWithSubTasksUseCase(repository = NoopTaskRepository) {
        var calls: Int = 0; private set
        override suspend operator fun invoke(id: Long): TaskWithSubTasks? {
            calls++
            return result
        }
    }

    private class FakeUpsertTaskUseCase(
        private val newRowId: Long = 42L,
    ) : UpsertTaskUseCase(repository = NoopTaskRepository) {
        var calls: Int = 0; private set
        var lastTask: Task? = null; private set
        var lastSubTasks: List<SubTask>? = null; private set
        override suspend operator fun invoke(task: Task, subTasks: List<SubTask>): Long {
            calls++
            lastTask = task
            lastSubTasks = subTasks
            return newRowId
        }
    }

    private class FakeDeleteTaskUseCase : DeleteTaskUseCase(repository = NoopTaskRepository) {
        var calls: Int = 0; private set
        override suspend operator fun invoke(task: Task) { calls++ }
    }

    private class FakeScheduleReminderUseCase : ScheduleReminderUseCase(scheduler = NoopReminderScheduler) {
        var calls: Int = 0; private set
        var lastTask: Task? = null; private set
        override suspend operator fun invoke(task: Task) {
            calls++
            lastTask = task
        }
    }

    private class FakeCancelReminderUseCase : CancelReminderUseCase(scheduler = NoopReminderScheduler) {
        var calls: Int = 0; private set
        var lastTaskId: Long? = null; private set
        override suspend operator fun invoke(taskId: Long) {
            calls++
            lastTaskId = taskId
        }
    }

    // ===== Fixture =====

    private lateinit var getTaskUseCase: FakeGetTaskWithSubTasksUseCase
    private lateinit var upsertTaskUseCase: FakeUpsertTaskUseCase
    private lateinit var deleteTaskUseCase: FakeDeleteTaskUseCase
    private lateinit var scheduleReminderUseCase: FakeScheduleReminderUseCase
    private lateinit var cancelReminderUseCase: FakeCancelReminderUseCase
    private lateinit var permissionRequester: FakeNotificationPermissionRequester

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getTaskUseCase = FakeGetTaskWithSubTasksUseCase()
        upsertTaskUseCase = FakeUpsertTaskUseCase(newRowId = 42L)
        deleteTaskUseCase = FakeDeleteTaskUseCase()
        scheduleReminderUseCase = FakeScheduleReminderUseCase()
        cancelReminderUseCase = FakeCancelReminderUseCase()
        permissionRequester = FakeNotificationPermissionRequester()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(taskId: Long = -1L): TaskEditViewModel {
        val savedState = SavedStateHandle(mapOf(Screen.EditTask.ARG_TASK_ID to taskId))
        return TaskEditViewModel(
            savedStateHandle = savedState,
            getTaskWithSubTasks = getTaskUseCase,
            upsertTask = upsertTaskUseCase,
            deleteTask = deleteTaskUseCase,
            scheduleReminder = scheduleReminderUseCase,
            cancelReminder = cancelReminderUseCase,
            notificationPermissionRequester = permissionRequester,
        )
    }

    // ===== 测试用例 =====

    @Test
    fun `save() with reminderTime=null does NOT call isGranted and schedules reminder (no-op)`() =
        runTest(testDispatcher) {
            val vm = makeViewModel()
            vm.onTitleChange("买菜")
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            assertEquals(1, upsertTaskUseCase.calls)
            assertEquals(1, scheduleReminderUseCase.calls)
            assertNotNull(scheduleReminderUseCase.lastTask)
            assertEquals("买菜", scheduleReminderUseCase.lastTask?.title)
            assertEquals(0, permissionRequester.isGrantedCalls)
            assertFalse(vm.uiState.value.shouldRequestNotificationPermission)
            assertTrue(vm.uiState.value.isSaved)
        }

    @Test
    fun `save() with reminderTime and permission already granted schedules reminder and skips shouldRequest`() =
        runTest(testDispatcher) {
            permissionRequester.granted = true
            val vm = makeViewModel()
            vm.onTitleChange("买菜")
            vm.onReminderChange(System.currentTimeMillis() + 3600_000L)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            assertEquals(1, upsertTaskUseCase.calls)
            assertEquals(1, scheduleReminderUseCase.calls)
            val scheduled = scheduleReminderUseCase.lastTask
            assertEquals(42L, scheduled?.id)
            assertNotNull(scheduled?.reminderTime)
            assertEquals(1, permissionRequester.isGrantedCalls)
            assertFalse(vm.uiState.value.shouldRequestNotificationPermission)
            assertTrue(vm.uiState.value.isSaved)
        }

    @Test
    fun `save() with reminderTime and permission NOT granted sets shouldRequest and skips schedule`() =
        runTest(testDispatcher) {
            permissionRequester.granted = false
            val vm = makeViewModel()
            vm.onTitleChange("买菜")
            vm.onReminderChange(System.currentTimeMillis() + 3600_000L)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            assertEquals(1, upsertTaskUseCase.calls)
            assertTrue(vm.uiState.value.isSaved)
            assertEquals(1, permissionRequester.isGrantedCalls)
            assertEquals(0, scheduleReminderUseCase.calls)
            assertTrue(vm.uiState.value.shouldRequestNotificationPermission)
            assertNull(vm.uiState.value.errorMessage)
        }

    @Test
    fun `onPermissionResult(true) schedules the pending reminder and clears shouldRequest`() =
        runTest(testDispatcher) {
            permissionRequester.granted = false
            val vm = makeViewModel()
            vm.onTitleChange("买菜")
            vm.onReminderChange(System.currentTimeMillis() + 3600_000L)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.shouldRequestNotificationPermission)
            assertEquals(0, scheduleReminderUseCase.calls)

            vm.onPermissionResult(granted = true)
            advanceUntilIdle()

            assertEquals(1, scheduleReminderUseCase.calls)
            val scheduled = scheduleReminderUseCase.lastTask
            assertEquals(42L, scheduled?.id)
            assertNotNull(scheduled?.reminderTime)
            assertFalse(vm.uiState.value.shouldRequestNotificationPermission)
            assertTrue(vm.uiState.value.isSaved)
            assertNull(vm.uiState.value.errorMessage)
        }

    @Test
    fun `onPermissionResult(false) sets errorMessage but keeps task saved and does not schedule`() =
        runTest(testDispatcher) {
            permissionRequester.granted = false
            val vm = makeViewModel()
            vm.onTitleChange("买菜")
            vm.onReminderChange(System.currentTimeMillis() + 3600_000L)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.shouldRequestNotificationPermission)
            assertEquals(0, scheduleReminderUseCase.calls)

            vm.onPermissionResult(granted = false)
            advanceUntilIdle()

            assertEquals(1, upsertTaskUseCase.calls)
            assertEquals(0, scheduleReminderUseCase.calls)
            assertTrue(vm.uiState.value.isSaved)
            assertFalse(vm.uiState.value.shouldRequestNotificationPermission)
            val err = vm.uiState.value.errorMessage
            assertNotNull(err)
            assertTrue(
                "errorMessage should mention permission denial: $err",
                err!!.contains("通知权限") || err.contains("不会响铃"),
            )
        }

    @Test
    fun `consumeShouldRequestNotificationPermission clears the one-shot signal`() =
        runTest(testDispatcher) {
            permissionRequester.granted = false
            val vm = makeViewModel()
            vm.onTitleChange("买菜")
            vm.onReminderChange(System.currentTimeMillis() + 3600_000L)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.shouldRequestNotificationPermission)

            vm.consumeShouldRequestNotificationPermission()
            assertFalse(vm.uiState.value.shouldRequestNotificationPermission)
        }

    @Test
    fun `consume then onPermissionResult(true) does not double-schedule`() =
        runTest(testDispatcher) {
            permissionRequester.granted = false
            val vm = makeViewModel()
            vm.onTitleChange("买菜")
            vm.onReminderChange(System.currentTimeMillis() + 3600_000L)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()
            vm.consumeShouldRequestNotificationPermission()
            advanceUntilIdle()

            // consume 只清信号，不清 pendingReminderTask —— onPermissionResult 仍能正常调度
            vm.onPermissionResult(granted = true)
            advanceUntilIdle()

            assertEquals(1, scheduleReminderUseCase.calls)
        }
}
