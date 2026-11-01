package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.MatrixCell
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskGroups
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskViewMode
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 8b'：[TaskViewModel] 显示偏好相关单测
 *
 * 重点：
 * - [TaskViewModel.uiState] 的初始值（[TaskUiState] 默认值）含 [TaskDisplayPreferences]
 * - 修改 alias / viewMode / selectedQuadrant 后：
 *   - Repository 状态正确（[FakeTaskDisplayRepository]）
 *   - uiState.value 经过 combine 后会更新（用 backgroundScope.collect + 立即断言验证）
 *
 * 沿用项目「Fake 优先」风格：所有 UseCase + Repository 手写 Fake，不引 Mockito。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelDisplayTest {

    private val testDispatcher = StandardTestDispatcher()

    // ===== Fake TaskRepository =====

    private open class FakeTaskRepository : TaskRepository {
        override fun observeActive() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeCompleted() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeTaskById(id: Long) = MutableStateFlow<Task?>(null)
        override fun observeStandaloneChecklist() = MutableStateFlow<List<SubTask>>(emptyList())
        override fun observeSubTasks(parentId: Long) = MutableStateFlow<List<SubTask>>(emptyList())
        override fun observeTasks(filter: TaskFilter, sort: TaskSort) =
            MutableStateFlow<List<Task>>(emptyList())
        override fun observeTasks(filter: FilterExpression, sort: TaskSort) =
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
        override fun searchTasks(keyword: String) =
            MutableStateFlow<List<Task>>(emptyList())
    }

    // ===== Fake TagRepository =====

    private open class FakeTagRepository : TagRepository {
        override fun observeAllTags() = MutableStateFlow<List<Tag>>(emptyList())
        override fun observeAllTagsWithCount() = MutableStateFlow<List<Tag>>(emptyList())
        override fun observeTagsForTask(taskId: Long) = MutableStateFlow<List<Tag>>(emptyList())
        override suspend fun createTag(displayName: String, colorHex: Long): Tag =
            Tag(id = 1L, nameNormalized = displayName.lowercase(), displayName = displayName, colorHex = colorHex)
        override suspend fun deleteTag(id: Long) {}
        override suspend fun addTagToTask(taskId: Long, tagId: Long) {}
        override suspend fun removeTagFromTask(taskId: Long, tagId: Long) {}
    }

    // ===== Fake TaskDisplayRepository（核心，记录所有写入） =====

    private class FakeTaskDisplayRepository : TaskDisplayRepository {
        private val state = MutableStateFlow(TaskDisplayPreferences())
        var setAliasCalls: Int = 0; private set
        var lastAliasQuadrant: TaskQuadrant? = null; private set
        var lastAliasValue: String? = null; private set
        var setViewModeCalls: Int = 0; private set
        var lastViewMode: TaskViewMode? = null; private set
        var selectQuadrantCalls: Int = 0; private set
        var lastSelectedQuadrant: TaskQuadrant? = null; private set

        override fun observe() = state

        override suspend fun setAlias(quadrant: TaskQuadrant, alias: String) {
            setAliasCalls++
            lastAliasQuadrant = quadrant
            lastAliasValue = alias
            state.value = state.value.copy(
                aliases = state.value.aliases + (quadrant to alias),
            )
        }

        override suspend fun setViewMode(mode: TaskViewMode) {
            setViewModeCalls++
            lastViewMode = mode
            state.value = state.value.copy(viewMode = mode)
        }

        override suspend fun selectQuadrant(quadrant: TaskQuadrant?) {
            selectQuadrantCalls++
            lastSelectedQuadrant = quadrant
            state.value = state.value.copy(selectedQuadrant = quadrant)
        }
    }

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun makeVm(
        taskRepo: FakeTaskRepository = FakeTaskRepository(),
        tagRepo: FakeTagRepository = FakeTagRepository(),
        displayRepo: FakeTaskDisplayRepository = FakeTaskDisplayRepository(),
    ): Pair<TaskViewModel, FakeTaskDisplayRepository> {
        // 4 个 open UseCase 直接 fake-subclass（沿用 TaskDetailViewModelTest 风格）
        val toggle = object : ToggleTaskCompleteUseCase(repository = taskRepo) {
            override suspend operator fun invoke(id: Long, completed: Boolean) {}
        }
        val delete = object : DeleteTaskUseCase(repository = taskRepo) {
            override suspend operator fun invoke(task: Task) {}
        }
        // 2 个 final UseCase 直接构造（其 invoke 委托给 fake repo）
        val observeTasks = ObserveTasksUseCase(taskRepo)
        val observeTags = ObserveAllTagsWithCountUseCase(tagRepo)

        val vm = TaskViewModel(
            observeTasks = observeTasks,
            observeAllTagsWithCount = observeTags,
            toggleTaskComplete = toggle,
            deleteTask = delete,
            taskDisplayRepository = displayRepo,
        )
        return vm to displayRepo
    }

    // ===== 测试策略 =====
    // 由于 uiState 使用 WhileSubscribed(5000)，直接读 uiState.value 只能拿到 initialValue。
    // 所以这些测试主要验证：
    // 1. initialValue 默认值正确（读 uiState.value）
    // 2. 调 VM action → Repo 状态正确（读 FakeRepo.observe().first()）
    // 3. 调 VM action → Repo 调用计数正确

    @Test
    fun `uiState displayPreferences 默认是 ALL + 全 alias = enum label`() = runTest {
        val (vm, _) = makeVm()
        val prefs = vm.uiState.value.displayPreferences
        // 直接读 initialValue（不依赖订阅）
        assertEquals(TaskViewMode.ALL, prefs.viewMode)
        assertNull(prefs.selectedQuadrant)
        // 因为初始 aliases = TaskDisplayPreferences.DEFAULT_ALIASES = enum.label 映射
        assertEquals(TaskDisplayPreferences.DEFAULT_ALIASES, prefs.aliases)
    }

    @Test
    fun `activeQuadrants 在 ALL 模式下返回全部象限（initialValue 行为）`() = runTest {
        val (vm, _) = makeVm()
        // 默认 viewMode = ALL → 5 个象限
        assertEquals(TaskQuadrant.entries.toList(), vm.uiState.value.activeQuadrants)
    }

    @Test
    fun `activeTaskCount 在初始 ALL 模式下为 0（groups 默认空）`() = runTest {
        val (vm, _) = makeVm()
        assertEquals(0, vm.uiState.value.activeTaskCount)
    }

    @Test
    fun `setAlias 把写入委托给 Repository（含参数）`() = runTest {
        val (vm, displayRepo) = makeVm()
        vm.setAlias(TaskQuadrant.P1, "🔥 立刻做")
        advanceUntilIdle()
        // 验证 FakeRepo 收到正确的写入
        assertEquals(1, displayRepo.setAliasCalls)
        assertEquals(TaskQuadrant.P1, displayRepo.lastAliasQuadrant)
        assertEquals("🔥 立刻做", displayRepo.lastAliasValue)
        // FakeRepo 的 state 也更新了
        val statePrefs = displayRepo.observe().first()
        assertEquals("🔥 立刻做", statePrefs.aliases[TaskQuadrant.P1])
    }

    @Test
    fun `setViewMode 把写入委托给 Repository（带自动兜底 P1）`() = runTest {
        val (vm, displayRepo) = makeVm()
        vm.setViewMode(TaskViewMode.BY_QUADRANT)
        advanceUntilIdle()
        // Repo 收到 setViewMode 调用
        assertEquals(1, displayRepo.setViewModeCalls)
        assertEquals(TaskViewMode.BY_QUADRANT, displayRepo.lastViewMode)
        // VM 自动兜底 selectedQuadrant = P1（因为 vm.setViewMode(BY_QUADRANT) 会连带 selectQuadrant(P1)）
        assertEquals(TaskQuadrant.P1, displayRepo.observe().first().selectedQuadrant)
    }

    @Test
    fun `selectQuadrant 同时写入 setViewMode + selectQuadrant`() = runTest {
        val (vm, displayRepo) = makeVm()
        vm.selectQuadrant(TaskQuadrant.P3)
        advanceUntilIdle()
        // 一次 selectQuadrant 调用应同时写两个 key
        assertEquals(1, displayRepo.setViewModeCalls)
        assertEquals(1, displayRepo.selectQuadrantCalls)
        assertEquals(TaskQuadrant.P3, displayRepo.lastSelectedQuadrant)
        // 最终 state 同时包含 viewMode 和 selectedQuadrant
        val statePrefs = displayRepo.observe().first()
        assertEquals(TaskViewMode.BY_QUADRANT, statePrefs.viewMode)
        assertEquals(TaskQuadrant.P3, statePrefs.selectedQuadrant)
    }

    @Test
    fun `showAll 写 viewMode=ALL + selectedQuadrant=null`() = runTest {
        val (vm, displayRepo) = makeVm()
        // 先切到某个象限
        vm.selectQuadrant(TaskQuadrant.P2)
        advanceUntilIdle()
        assertEquals(TaskQuadrant.P2, displayRepo.observe().first().selectedQuadrant)
        // 回到全部
        vm.showAll()
        advanceUntilIdle()
        // setViewMode(ALL) + selectQuadrant(null) 各 1 次
        assertEquals(2, displayRepo.setViewModeCalls)
        assertEquals(2, displayRepo.selectQuadrantCalls)
        assertEquals(TaskViewMode.ALL, displayRepo.observe().first().viewMode)
        assertNull(displayRepo.observe().first().selectedQuadrant)
    }

    @Test
    fun `连续调用 setAlias 多次都正确写入 Repo`() = runTest {
        val (vm, displayRepo) = makeVm()
        vm.setAlias(TaskQuadrant.P1, "立刻做")
        vm.setAlias(TaskQuadrant.P2, "安排做")
        vm.setAlias(TaskQuadrant.P3, "委托")
        advanceUntilIdle()

        // 3 次 alias 写入
        assertEquals(3, displayRepo.setAliasCalls)
        // 最终 state 都有
        val statePrefs = displayRepo.observe().first()
        assertEquals("立刻做", statePrefs.aliases[TaskQuadrant.P1])
        assertEquals("安排做", statePrefs.aliases[TaskQuadrant.P2])
        assertEquals("委托", statePrefs.aliases[TaskQuadrant.P3])
    }

    @Test
    fun `alias 在多次 setViewMode 和 showAll 后仍保留`() = runTest {
        val (vm, displayRepo) = makeVm()
        vm.setAlias(TaskQuadrant.P1, "立刻做")
        vm.setViewMode(TaskViewMode.BY_QUADRANT)
        vm.showAll()
        advanceUntilIdle()

        // alias 写入 1 次，viewMode 写入 2 次（setViewMode + showAll 内的 setViewMode(ALL)）
        assertEquals(1, displayRepo.setAliasCalls)
        assertEquals(2, displayRepo.setViewModeCalls)
        // alias 依然在
        assertEquals("立刻做", displayRepo.observe().first().aliases[TaskQuadrant.P1])
    }

    @Test
    fun `setViewMode 切到 BY_QUADRANT 时 VM 自动兜底 selectedQuadrant 为 P1`() = runTest {
        val (vm, displayRepo) = makeVm()
        // 默认 selectedQuadrant = null
        assertNull(displayRepo.observe().first().selectedQuadrant)
        vm.setViewMode(TaskViewMode.BY_QUADRANT)
        advanceUntilIdle()
        // 关键：setViewMode 应当附带把 selectedQuadrant 兜底为 P1
        assertEquals(1, displayRepo.setViewModeCalls)
        assertEquals(1, displayRepo.selectQuadrantCalls)
        assertEquals(TaskQuadrant.P1, displayRepo.lastSelectedQuadrant)
    }

    @Test
    fun `showMatrix 只写 viewMode=MATRIX 不动 selectedQuadrant`() = runTest {
        // Phase 8final G2：矩阵与全部视图共用同一份数据，不应干涉象限选中
        val (vm, displayRepo) = makeVm()
        vm.showMatrix()
        advanceUntilIdle()
        assertEquals(1, displayRepo.setViewModeCalls)
        assertEquals(TaskViewMode.MATRIX, displayRepo.lastViewMode)
        // 关键：不额外调 selectQuadrant（矩阵不依赖它）
        assertEquals(0, displayRepo.selectQuadrantCalls)
        assertNull(displayRepo.observe().first().selectedQuadrant)
    }

    @Test
    fun `从矩阵切回列表会清掉 selectedQuadrant`() = runTest {
        val (vm, displayRepo) = makeVm()
        vm.showMatrix()
        advanceUntilIdle()
        vm.showAll()
        advanceUntilIdle()
        assertEquals(2, displayRepo.setViewModeCalls)
        assertEquals(TaskViewMode.ALL, displayRepo.observe().first().viewMode)
        assertNull(displayRepo.observe().first().selectedQuadrant)
    }

    @Test
    fun `点分组 Tab 可以从矩阵退出`() = runTest {
        // 用户路径：矩阵 → 点 P3 Tab → 切到 BY_QUADRANT
        val (vm, displayRepo) = makeVm()
        vm.showMatrix()
        advanceUntilIdle()
        vm.selectQuadrant(TaskQuadrant.P3)
        advanceUntilIdle()
        val statePrefs = displayRepo.observe().first()
        assertEquals(TaskViewMode.BY_QUADRANT, statePrefs.viewMode)
        assertEquals(TaskQuadrant.P3, statePrefs.selectedQuadrant)
    }

    @Test
    fun `MATRIX 模式下 activeQuadrants 与 ALL 一致（同一份数据）`() {
        // 矩阵不重新分类：它看到的全量象限必须等于 ALL
        val allState = TaskUiState()
        val matrixState = TaskUiState(
            displayPreferences = TaskDisplayPreferences(viewMode = TaskViewMode.MATRIX),
        )
        assertEquals(allState.activeQuadrants, matrixState.activeQuadrants)
    }

    @Test
    fun `matrix 属性从 groups 派生且与 viewMode 无关`() {
        // 即使不在矩阵模式，matrix 也应能算出同样结果（纯派生，不依赖当前视图）
        val groups = TaskGroups(
            byQuadrant = mapOf(TaskQuadrant.P1 to listOf(Task(id = 7L, title = "a", dueDate = 1L))),
            completed = emptyList(),
        )
        val forList = TaskUiState(groups = groups)
        val forMatrix = TaskUiState(
            groups = groups,
            displayPreferences = TaskDisplayPreferences(viewMode = TaskViewMode.MATRIX),
        )
        assertEquals(1, forList.matrix.countOf(MatrixCell.CELL_0))
        assertEquals(forList.matrix, forMatrix.matrix)
    }

    @Test
    fun `TaskDisplayPreferences 默认 aliases 与 enum label 严格对齐`() {
        // 静态断言：DEFAULT_ALIASES 必须等于 enum.label
        // 不依赖任何 VM / 协程，是单测覆盖默认值的最快方式
        val defaults = TaskDisplayPreferences.DEFAULT_ALIASES
        TaskQuadrant.entries.forEach { q ->
            assertEquals(
                "象限 ${q.name} 默认 alias",
                q.label,
                defaults[q],
            )
        }
        // aliasOf 在缺失 alias 时回退到 enum label
        val empty = TaskDisplayPreferences(aliases = emptyMap())
        TaskQuadrant.entries.forEach { q ->
            assertEquals(q.label, TaskDisplayPreferences.aliasOf(empty, q))
        }
    }
}
