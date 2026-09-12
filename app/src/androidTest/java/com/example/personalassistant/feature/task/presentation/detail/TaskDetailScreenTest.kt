package com.example.personalassistant.feature.task.presentation.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import com.example.personalassistant.feature.task.domain.repository.TagRepository
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import com.example.personalassistant.feature.task.domain.usecase.AddSubTaskToTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.AddTagToTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.CreateTagUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteSubTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveSubTasksUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveTagsForTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.RemoveTagFromTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleSubTaskCompleteUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleTaskCompleteUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Phase 6b：TaskDetailScreen Compose UI 接线测试（androidTest/instrumented）。
 *
 * 设计：fork [TaskDetailViewModel] 子类 [FakeTaskDetailViewModel] 直接喂初始 state。
 * 不用 Hilt —— 沿用项目 TaskEditScreenTest 同模式。
 *
 * 覆盖：
 * 1. 任务存在时：BasicInfo 标题 + SubTask 列表渲染
 * 2. 任务变 null 时：自动回调 onBack（删除场景）
 */
class TaskDetailScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ===== Fake TaskRepository =====

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
        override fun observeTasks(filter: FilterExpression, sort: TaskSort): Flow<List<Task>> =
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
        override fun searchTasks(keyword: String): Flow<List<Task>> =
            MutableStateFlow<List<Task>>(emptyList())
    }

    // ===== Phase 6c：Tag 侧 No-op Fake =====
    //
    // 详情页标签流在本测试里恒空（断言只覆盖标题/子清单），用共享 object 即可，
    // 避免为 ObserveTagsForTaskUseCase 再 fork 一层（它是 final class，不可继承）。
    private object NoopTagRepository : TagRepository {
        override fun observeAllTags() = MutableStateFlow<List<Tag>>(emptyList())
        override fun observeAllTagsWithCount() = MutableStateFlow<List<Tag>>(emptyList())
        override fun observeTagsForTask(taskId: Long) = MutableStateFlow<List<Tag>>(emptyList())
        override suspend fun createTag(displayName: String, colorHex: Long): Tag = Tag(
            id = -1L,
            nameNormalized = Tag.normalize(displayName),
            displayName = displayName,
            colorHex = colorHex,
        )
        override suspend fun deleteTag(id: Long) {}
        override suspend fun addTagToTask(taskId: Long, tagId: Long) {}
        override suspend fun removeTagFromTask(taskId: Long, tagId: Long) {}
    }

    private class FakeObserveSubTasksUseCase(
        private val repo: FakeTaskRepository,
    ) : ObserveSubTasksUseCase(repository = repo) {
        override fun invoke(parentId: Long) = repo.subTaskFlow
    }

    private class NoopAddSubTaskToTaskUseCase(
        private val repo: FakeTaskRepository,
    ) : AddSubTaskToTaskUseCase(repository = repo) {
        override suspend operator fun invoke(parentId: Long, title: String): Long = -1L
    }

    private class NoopDeleteSubTaskUseCase(
        private val repo: FakeTaskRepository,
    ) : DeleteSubTaskUseCase(repository = repo) {
        override suspend operator fun invoke(subTask: SubTask) {}
    }

    private class NoopToggleTaskCompleteUseCase(
        private val repo: FakeTaskRepository,
    ) : ToggleTaskCompleteUseCase(repository = repo) {
        override suspend operator fun invoke(id: Long, completed: Boolean) {}
    }

    private class NoopToggleSubTaskCompleteUseCase(
        private val repo: FakeTaskRepository,
    ) : ToggleSubTaskCompleteUseCase(repository = repo) {
        override suspend operator fun invoke(id: Long, completed: Boolean) {}
    }

    /**
     * Fork [TaskDetailViewModel]：把 uiState 替换为外部喂的 StateFlow，
     * 方便测试直接控制详情页渲染内容而不依赖 combine + repository。
     */
    private class FakeTaskDetailViewModel(
        initialTask: Task?,
        initialSubTasks: List<SubTask>,
    ) : TaskDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(ARG_TASK_ID to 1L)),
        observeSubTasks = FakeObserveSubTasksUseCase(FakeTaskRepository()),
        observeTagsForTask = ObserveTagsForTaskUseCase(NoopTagRepository),
        tagRepository = NoopTagRepository,
        repository = FakeTaskRepository(),
        toggleTaskComplete = NoopToggleTaskCompleteUseCase(FakeTaskRepository()),
        toggleSubTaskCompleteUseCase = NoopToggleSubTaskCompleteUseCase(FakeTaskRepository()),
        deleteSubTaskUseCase = NoopDeleteSubTaskUseCase(FakeTaskRepository()),
        addSubTaskToTaskUseCase = NoopAddSubTaskToTaskUseCase(FakeTaskRepository()),
        createTagUseCase = CreateTagUseCase(NoopTagRepository),
        addTagToTaskUseCase = AddTagToTaskUseCase(NoopTagRepository),
        removeTagFromTaskUseCase = RemoveTagFromTaskUseCase(NoopTagRepository),
    ) {
        private val fakeRepo = FakeTaskRepository(
            taskFlow = MutableStateFlow(initialTask),
            subTaskFlow = MutableStateFlow(initialSubTasks),
        )
        // 替换 observeTaskById 流（避免 super 构造时 MutableStateFlow 的延迟 emit）
        override val uiState: kotlinx.coroutines.flow.StateFlow<TaskDetailUiState> =
            MutableStateFlow(
                TaskDetailUiState(
                    isLoading = false,
                    task = initialTask,
                    subTasks = initialSubTasks,
                )
            )
    }

    @Test
    fun taskDetailScreen_rendersTitleAndSubTasks() {
        val task = Task(
            id = 1L,
            title = "完成 Phase 6b",
            priority = TaskPriority.HIGH,
        )
        val subTasks = listOf(
            SubTask(id = 10L, parentTaskId = 1L, title = "设计 VM"),
            SubTask(id = 11L, parentTaskId = 1L, title = "写 UI"),
        )
        val fakeVm = FakeTaskDetailViewModel(
            initialTask = task,
            initialSubTasks = subTasks,
        )

        composeTestRule.setContent {
            TaskDetailScreen(
                taskId = 1L,
                onBack = {},
                onEdit = {},
                viewModel = fakeVm,
            )
        }
        composeTestRule.waitForIdle()

        // AppBar 标题
        composeTestRule.onNodeWithText("任务详情").assertIsDisplayed()
        // BasicInfo section 任务标题
        composeTestRule.onNodeWithText("完成 Phase 6b").assertIsDisplayed()
        // SubTask section 标题
        composeTestRule.onNodeWithText("子清单").assertIsDisplayed()
        // 子清单条目
        composeTestRule.onNodeWithText("设计 VM").assertIsDisplayed()
        composeTestRule.onNodeWithText("写 UI").assertIsDisplayed()
        // Section 进度 badge: 0/2
        composeTestRule.onNodeWithText("0/2").assertIsDisplayed()
    }

    @Test
    fun taskDetailScreen_taskBecomesNull_callsOnBack() {
        val fakeVm = FakeTaskDetailViewModel(
            initialTask = null,
            initialSubTasks = emptyList(),
        )
        var onBackCalled = false

        composeTestRule.setContent {
            TaskDetailScreen(
                taskId = 1L,
                onBack = { onBackCalled = true },
                onEdit = {},
                viewModel = fakeVm,
            )
        }
        composeTestRule.waitForIdle()

        assertTrue("task == null 时应触发 onBack", onBackCalled)
    }

    @Test
    fun taskDetailScreen_emptySubTasks_showsHint() {
        val task = Task(id = 1L, title = "无子清单任务", priority = TaskPriority.MEDIUM)
        val fakeVm = FakeTaskDetailViewModel(
            initialTask = task,
            initialSubTasks = emptyList(),
        )

        composeTestRule.setContent {
            TaskDetailScreen(
                taskId = 1L,
                onBack = {},
                onEdit = {},
                viewModel = fakeVm,
            )
        }
        composeTestRule.waitForIdle()

        // 任务标题渲染
        composeTestRule.onNodeWithText("无子清单任务").assertIsDisplayed()
        // 空状态提示
        composeTestRule.onNodeWithText("还没有子任务，试试在下面加一条 ♪").assertIsDisplayed()
    }
}
