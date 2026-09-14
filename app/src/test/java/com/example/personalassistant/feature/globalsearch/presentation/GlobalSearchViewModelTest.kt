package com.example.personalassistant.feature.globalsearch.presentation

import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.globalsearch.domain.usecase.GlobalSearchUseCase
import com.example.personalassistant.feature.task.data.repository.FakeTaskRepository
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.timetable.data.repository.FakeTimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 8 Sub-step 3：[GlobalSearchViewModel] 单元测试
 *
 * 覆盖 3 个核心场景：
 * 1. 默认初始 state 为空（query="" + hasQuery=false）
 * 2. 输入关键字 → debounce 后 state 更新为命中列表
 * 3. clearQuery() 把 query 重置为 ""
 *
 * - debounce = 250ms，用 `advanceTimeBy(260)` 跨过窗口
 * - 用 `uiState.first { ... }` 触发订阅 + 等待值（StateFlow WhileSubscribed 需要 subscriber）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var taskRepo: FakeTaskRepository
    private lateinit var calendarRepo: FakeCalendarRepository
    private lateinit var financeRepo: FakeFinanceRepository
    private lateinit var timetableRepo: FakeTimetableRepository
    private lateinit var vm: GlobalSearchViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        taskRepo = FakeTaskRepository()
        calendarRepo = FakeCalendarRepository()
        financeRepo = FakeFinanceRepository()
        timetableRepo = FakeTimetableRepository()
        val useCase = GlobalSearchUseCase(
            com.example.personalassistant.feature.globalsearch.data.repository.GlobalSearchRepositoryImpl(
                taskRepository = taskRepo,
                calendarRepository = calendarRepo,
                financeRepository = financeRepo,
                timetableRepository = timetableRepo,
            )
        )
        vm = GlobalSearchViewModel(useCase)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty query`() = runTest(testDispatcher) {
        // 刚创建的 VM：state 应处于默认值（用 first 触发订阅）
        val state = vm.uiState.first()
        assertEquals("", state.query)
        assertFalse(state.hasQuery)
        assertFalse(state.hasResults)
    }

    @Test
    fun `query input triggers debounced search`() = runTest(testDispatcher) {
        // 种一条任务
        taskRepo.seedTask(
            Task(
                id = 1L,
                title = "复习准备",
                description = "明天的考试",
                priority = TaskPriority.MEDIUM,
                dueDate = 1_700_000_000_000L,
            )
        )

        // 输入关键字
        vm.onQueryChange("复习")
        // 推进时间跨过 debounce（250ms）+ 让 combine 4 路 + stateIn 完成
        advanceTimeBy(260)
        advanceUntilIdle()

        // 用 first { hasResults } 等待 uiState 真正带上数据
        val state = vm.uiState.first { it.hasResults }
        assertEquals("复习", state.query)
        assertEquals(1, state.result.tasks.size)
        assertEquals("复习准备", state.result.tasks.first().title)
    }

    @Test
    fun `clearQuery resets state`() = runTest(testDispatcher) {
        // 先输入并等 debounce
        vm.onQueryChange("任意关键字")
        advanceTimeBy(260)
        advanceUntilIdle()
        val populated = vm.uiState.first { it.query == "任意关键字" }
        assertEquals("任意关键字", populated.query)

        // 清空
        vm.clearQuery()
        advanceUntilIdle()
        val cleared = vm.uiState.first { it.query == "" && !it.hasQuery }
        assertEquals("", cleared.query)
        assertFalse(cleared.hasQuery)
        assertFalse(cleared.hasResults)
    }
}
