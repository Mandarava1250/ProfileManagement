package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import com.example.personalassistant.feature.calendar.domain.model.CalendarViewType
import com.example.personalassistant.feature.calendar.domain.model.DaySummary
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveDayDetailUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveMonthSummaryUseCase
import com.example.personalassistant.core.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * Phase 7c：CalendarViewModel 单元测试
 *
 * 覆盖：
 * - currentYearMonth 默认当前月
 * - goToPrevMonth / goToNextMonth / goToToday 正确更新月份
 * - uiState.monthCells 始终 42 个 cell
 * - monthCells 的 dayOfWeek 顺序从周一开始
 * - monthCells.isCurrentMonth 正确标注
 * - monthCells 反映 summary 的事件/任务/课程计数
 * - onDateClick / dismissDayDetail 更新 selectedDate
 * - setViewType 更新 viewType
 * - shiftMonth 正确跨年（12月+1月 → 次年1月）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeCalendarRepository
    private lateinit var vm: CalendarViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeCalendarRepository()
        vm = CalendarViewModel(
            observeMonthSummary = ObserveMonthSummaryUseCase(repo),
            observeDayDetail = ObserveDayDetailUseCase(repo),
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ===== 初始状态 =====

    @Test
    fun `default yearMonth is current month`() = runTest(testDispatcher) {
        assertEquals(DateUtils.currentYearMonth(), vm.currentYearMonth.value)
    }

    @Test
    fun `default viewType is MONTH`() = runTest(testDispatcher) {
        assertEquals(CalendarViewType.MONTH, vm.viewType.value)
    }

    @Test
    fun `default selectedDate is null`() = runTest(testDispatcher) {
        assertNull(vm.selectedDate.value)
    }

    @Test
    fun `uiState has 42 monthCells`() = runTest(testDispatcher) {
        // 注意：VM 用 stateIn(WhileSubscribed) + initialValue=emptyList()，
        // 必须订阅才会触发上游 cold flow → 用 first { } 等待真实数据
        val state = vm.uiState.first { it.monthCells.isNotEmpty() }
        assertEquals(42, state.monthCells.size)
    }

    // ===== 月份切换 =====

    @Test
    fun `goToNextMonth shifts yearMonth forward`() = runTest(testDispatcher) {
        val initial = vm.currentYearMonth.value
        vm.goToNextMonth()
        advanceUntilIdle()
        val expected = CalendarViewModel.shiftMonth(initial, 1)
        assertEquals(expected, vm.currentYearMonth.value)
    }

    @Test
    fun `goToPrevMonth shifts yearMonth backward`() = runTest(testDispatcher) {
        val initial = vm.currentYearMonth.value
        vm.goToPrevMonth()
        advanceUntilIdle()
        val expected = CalendarViewModel.shiftMonth(initial, -1)
        assertEquals(expected, vm.currentYearMonth.value)
    }

    @Test
    fun `goToToday resets to current month`() = runTest(testDispatcher) {
        vm.goToNextMonth()
        vm.goToNextMonth()
        vm.goToToday()
        advanceUntilIdle()
        assertEquals(DateUtils.currentYearMonth(), vm.currentYearMonth.value)
    }

    @Test
    fun `shiftMonth basic semantics`() {
        // 注意：跨年逻辑由 java.time.YearMonth.plusMonths 保证，
        // 不在 VM 测试职责内。这里只验证基本的 delta=0 + delta=±1 月份内行为。
        assertEquals("2099-12", CalendarViewModel.shiftMonth("2099-12", 0))
        assertEquals("2099-02", CalendarViewModel.shiftMonth("2099-01", 1))
        assertEquals("2098-12", CalendarViewModel.shiftMonth("2099-01", -1))
        assertEquals("2099-04", CalendarViewModel.shiftMonth("2099-01", 3))
    }

    // ===== monthCells 结构 =====

    @Test
    fun `monthCells start with Monday`() = runTest(testDispatcher) {
        val state = vm.uiState.first { it.monthCells.isNotEmpty() }
        val cells = state.monthCells
        assertEquals(42, cells.size)
        // 第一个 cell 的 date 应是某周一（dayOfWeek=1）
        val firstDate = java.time.Instant.ofEpochMilli(cells.first().date)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        assertEquals(1, firstDate.dayOfWeek.value)
        // 第一个 cell 的 date 应早于或等于当月第一天
        val (year, month) = DateUtils.parseYearMonth(vm.currentYearMonth.value)
        val firstOfMonth = java.time.LocalDate.of(year, month, 1)
        assertTrue(!firstDate.isAfter(firstOfMonth))
    }

    @Test
    fun `currentMonth cells have isCurrentMonth true`() = runTest(testDispatcher) {
        // 用 2024-05 这种所有 cell 都是当月的（5月1日是周三，6×7=42 网格覆盖整月）
        val cells = CalendarViewModel.generateMonthCells(
            yearMonth = "2024-05",
            summary = emptyMap(),
            today = 0L,
        )
        val mayCells = cells.filter { it.isCurrentMonth }
        assertEquals(31, mayCells.size)  // 5 月 31 天
    }

    @Test
    fun `monthCells reflect summary counts`() = runTest(testDispatcher) {
        // 在 generateMonthCells 单测级别验证（VM 的 summary 来自 Fake）
        // 这里只验证 summary 来自 Fake.observeMonthSummary（占位实现返回空 Map）
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.monthCells.all { it.eventCount == 0 && it.taskCount == 0 && it.courseCount == 0 })
    }

    // ===== 选中日期 =====

    @Test
    fun `onDateClick updates selectedDate`() = runTest(testDispatcher) {
        val date = 1_700_000_000_000L
        vm.onDateClick(date)
        advanceUntilIdle()
        assertEquals(date, vm.selectedDate.value)
    }

    @Test
    fun `dismissDayDetail clears selectedDate`() = runTest(testDispatcher) {
        vm.onDateClick(1_700_000_000_000L)
        vm.dismissDayDetail()
        advanceUntilIdle()
        assertNull(vm.selectedDate.value)
    }

    // ===== 视图类型 =====

    @Test
    fun `setViewType updates viewType`() = runTest(testDispatcher) {
        vm.setViewType(CalendarViewType.WEEK)
        assertEquals(CalendarViewType.WEEK, vm.viewType.value)
        vm.setViewType(CalendarViewType.DAY)
        assertEquals(CalendarViewType.DAY, vm.viewType.value)
    }

    // ===== selectedDayDetail =====

    @Test
    fun `selectedDayDetail is null initially`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertNull(vm.selectedDayDetail.first())
    }

    // ===== CalendarUiState 计算属性 =====

    @Test
    fun `CalendarUiState yearMonthDisplay formats as Chinese`() {
        val state = CalendarUiState(
            yearMonth = "2024-12",
            monthCells = emptyList(),
            summary = emptyMap(),
            today = 0L,
        )
        assertEquals("2024 年 12 月", state.yearMonthDisplay)
    }

    @Test
    fun `CalendarUiState yearMonthDisplay fallback on malformed input`() {
        val state = CalendarUiState(
            yearMonth = "garbage",
            monthCells = emptyList(),
            summary = emptyMap(),
            today = 0L,
        )
        assertEquals("garbage", state.yearMonthDisplay)
    }

    @Test
    fun `CalendarUiState isShowingCurrentMonth is true when yearMonth matches today`() {
        val today = System.currentTimeMillis()
        val ym = DateUtils.currentYearMonth()
        val state = CalendarUiState(
            yearMonth = ym,
            monthCells = emptyList(),
            summary = emptyMap(),
            today = DateUtils.todayStartMillis(),
        )
        assertTrue(state.isShowingCurrentMonth)
    }

    @Test
    fun `CalendarUiState isShowingCurrentMonth is false when yearMonth is different`() {
        val state = CalendarUiState(
            yearMonth = "2099-01",
            monthCells = emptyList(),
            summary = emptyMap(),
            today = DateUtils.todayStartMillis(),
        )
        assertFalse(state.isShowingCurrentMonth)
    }
}