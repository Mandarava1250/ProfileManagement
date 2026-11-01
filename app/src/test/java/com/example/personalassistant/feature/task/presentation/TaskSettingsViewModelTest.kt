package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskViewMode
import com.example.personalassistant.feature.task.domain.repository.TaskDisplayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Phase 13：TaskSettingsViewModel 单元测试
 *
 * 任务分组的长期设置只有「象限别名」，所以这个 VM 的职责很窄：
 * - 读出当前显示偏好
 * - 改写某象限的别名
 * - 空别名不写（否则界面会退化成空白 Tab）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeTaskDisplayRepository
    private lateinit var vm: TaskSettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeTaskDisplayRepository()
        vm = TaskSettingsViewModel(displayRepository = repo)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `preferences reflect the repository`() = runTest(testDispatcher) {
        repo.state.value = TaskDisplayPreferences(
            aliases = TaskQuadrant.entries.associateWith { "改过" },
        )
        advanceUntilIdle()

        val prefs = vm.preferences.value
        assertEquals("改过", TaskDisplayPreferences.aliasOf(prefs, TaskQuadrant.P1))
    }

    @Test
    fun `setAlias writes trimmed alias to repository`() = runTest(testDispatcher) {
        vm.setAlias(TaskQuadrant.P1, "  马上做  ")
        advanceUntilIdle()

        assertEquals("马上做", repo.aliases[TaskQuadrant.P1])
    }

    @Test
    fun `setAlias with blank value is ignored`() = runTest(testDispatcher) {
        vm.setAlias(TaskQuadrant.P2, "   ")
        advanceUntilIdle()

        assertNull(repo.aliases[TaskQuadrant.P2])
    }
}

/**
 * 内存版 [TaskDisplayRepository]
 *
 * 零 IO，`runTest` 的虚拟时间能完整推进 —— 行为完全确定。
 */
private class FakeTaskDisplayRepository : TaskDisplayRepository {

    val state = MutableStateFlow(TaskDisplayPreferences())
    val aliases = mutableMapOf<TaskQuadrant, String>()

    override fun observe(): Flow<TaskDisplayPreferences> = state

    override suspend fun setAlias(quadrant: TaskQuadrant, alias: String) {
        aliases[quadrant] = alias
        state.value = state.value.copy(aliases = state.value.aliases + (quadrant to alias))
    }

    override suspend fun setViewMode(mode: TaskViewMode) {
        state.value = state.value.copy(viewMode = mode)
    }

    override suspend fun selectQuadrant(quadrant: TaskQuadrant?) {
        state.value = state.value.copy(selectedQuadrant = quadrant)
    }
}
