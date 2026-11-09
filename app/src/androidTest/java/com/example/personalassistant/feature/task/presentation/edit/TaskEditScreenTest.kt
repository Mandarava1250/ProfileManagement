package com.example.personalassistant.feature.task.presentation.edit

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import com.example.personalassistant.core.notification.FakeNotificationPermissionRequester
import com.example.personalassistant.feature.task.domain.model.FilterExpression
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Phase 5b: TaskEditScreen Compose UI 接线测试（androidTest/instrumented）。
 *
 * 目标：验证 VM.shouldRequestNotificationPermission=true 时 Composable 渲染稳定
 * + 验证 ViewModel.consumeShouldRequestNotificationPermission() 在 LaunchedEffect 触发后被调。
 *
 * 设计：fork [TaskEditViewModel] 子类 [FakeTaskEditViewModel] 直接喂初始 state。
 * 不用 Hilt —— 避开 HiltAndroidTest + Robolectric 的复杂配置。
 */
class TaskEditScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ===== No-op 接口实现 =====

    private object NoopTaskRepository : TaskRepository {
        override fun observeActive() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeCompleted() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeTaskById(id: Long) = MutableStateFlow<Task?>(null)
        override fun observeStandaloneChecklist() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeSubTasks(parentId: Long) = MutableStateFlow<List<Task>>(emptyList())
        override fun observeTasks(filter: TaskFilter, sort: TaskSort) =
            MutableStateFlow<List<Task>>(emptyList())
        override fun observeTasks(filter: FilterExpression, sort: TaskSort): Flow<List<Task>> =
            MutableStateFlow<List<Task>>(emptyList())
        override suspend fun getTaskWithSubTasks(id: Long): TaskWithSubTasks? = null
        override suspend fun upsertTask(task: Task, subTasks: List<Task>): Long = -1L
        override suspend fun deleteTask(task: Task) {}
        override suspend fun toggleTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun toggleSubTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun addStandaloneSubTask(title: String): Long = -1L
        override suspend fun addSubTaskToTask(parentId: Long, title: String): Long = -1L
        override suspend fun deleteSubTask(subTask: Task) {}
        override suspend fun reorderTasks(orderedIds: List<Long>) {}
        override fun searchTasks(keyword: String): Flow<List<Task>> =
            MutableStateFlow<List<Task>>(emptyList())
    }

    private object NoopReminderScheduler : ReminderScheduler {
        override suspend fun schedule(info: ReminderInfo) {}
        override suspend fun cancel(taskId: Long) {}
        override suspend fun cancelAll() {}
    }

    private class NoopGetTask : GetTaskWithSubTasksUseCase(repository = NoopTaskRepository) {
        override suspend operator fun invoke(id: Long): TaskWithSubTasks? = null
    }
    private class NoopUpsertTask : UpsertTaskUseCase(repository = NoopTaskRepository) {
        override suspend operator fun invoke(task: Task, subTasks: List<Task>): Long = -1L
    }
    private class NoopDeleteTask : DeleteTaskUseCase(repository = NoopTaskRepository) {
        override suspend operator fun invoke(task: Task) {}
    }
    // Phase 8 Sub-step 7：ScheduleReminderUseCase 新增 isHolidayUseCase 依赖。
    // IsHolidayUseCase 是 fun interface，直接给常量 false stub，
    // 与 JVM 侧 TaskEditViewModelTest 同写法，保持「不避让」的原测试语义。
    private class NoopScheduleReminder : ScheduleReminderUseCase(
        scheduler = NoopReminderScheduler,
        isHolidayUseCase = com.example.personalassistant.core.holiday.usecase.IsHolidayUseCase { false },
    ) {
        override suspend operator fun invoke(task: Task) {}
    }
    private class NoopCancelReminder : CancelReminderUseCase(scheduler = NoopReminderScheduler) {
        override suspend operator fun invoke(taskId: Long) {}
    }

    /**
     * Phase 5b: 暴露 init 钩子直接喂 state。
     * 利用 protected `_uiState` 直接覆写初始值。
     */
    private class FakeTaskEditViewModel(
        initialState: TaskEditUiState,
    ) : TaskEditViewModel(
        savedStateHandle = SavedStateHandle(),
        getTaskWithSubTasks = NoopGetTask(),
        upsertTask = NoopUpsertTask(),
        deleteTask = NoopDeleteTask(),
        scheduleReminder = NoopScheduleReminder(),
        cancelReminder = NoopCancelReminder(),
        notificationPermissionRequester = FakeNotificationPermissionRequester(),
    ) {
        init {
            _uiState.value = initialState
        }

        /** 暴露计数器供测试断言 LaunchedEffect 触发了 consume。 */
        var consumeCalls: Int = 0; private set
        override fun consumeShouldRequestNotificationPermission() {
            consumeCalls++
            super.consumeShouldRequestNotificationPermission()
        }
    }

    @Test
    fun when_shouldRequestPermission_false_then_composable_renders_without_crashing() {
        val fakeVm = FakeTaskEditViewModel(
            initialState = TaskEditUiState(
                isNew = true,
                isLoading = false,
                shouldRequestNotificationPermission = false,
            ),
        )
        composeTestRule.setContent {
            TaskEditScreen(
                taskId = -1L,
                onBack = {},
                viewModel = fakeVm,
            )
        }
        composeTestRule.onNodeWithText("新建任务").assertIsDisplayed()
        assert(fakeVm.consumeCalls == 0) {
            "consumeCalls should be 0 but was ${fakeVm.consumeCalls}"
        }
    }

    @Test
    fun when_shouldRequestPermission_true_then_LaunchedEffect_consumes_signal() {
        val fakeVm = FakeTaskEditViewModel(
            initialState = TaskEditUiState(
                isNew = true,
                isLoading = false,
                shouldRequestNotificationPermission = true,
            ),
        )
        composeTestRule.setContent {
            TaskEditScreen(
                taskId = -1L,
                onBack = {},
                viewModel = fakeVm,
            )
        }
        composeTestRule.waitForIdle()
        assert(fakeVm.consumeCalls >= 1) {
            "consumeCalls should be >= 1 but was ${fakeVm.consumeCalls}"
        }
        assert(!fakeVm.uiState.value.shouldRequestNotificationPermission) {
            "shouldRequestNotificationPermission should be false after consume"
        }
    }
}
