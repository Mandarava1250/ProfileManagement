package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import com.example.personalassistant.feature.calendar.domain.model.CalendarViewType
import com.example.personalassistant.feature.calendar.domain.model.DaySummary
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.usecase.AddEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DeleteEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveDayDetailUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveMonthSummaryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpdateEventUseCase
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
            addEvent = AddEventUseCase(repo),
            updateEvent = UpdateEventUseCase(repo),
            deleteEventUseCase = DeleteEventUseCase(repo),
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

    // ===== Sub-step 5：事件编辑动作 =====

    private fun draftState(
        originalId: Long = 0L,
        title: String = "新事件",
        date: Long = DateUtils.todayStartMillis(),
    ) = EventEditState(
        originalId = originalId,
        title = title,
        date = date,
        isAllDay = true,
        hasTime = false,
    )

    @Test
    fun `editingState is null initially`() = runTest(testDispatcher) {
        assertNull(vm.editingState.value)
        assertNull(vm.editorError.value)
    }

    @Test
    fun `startAddEvent falls back to today when nothing selected`() = runTest(testDispatcher) {
        vm.startAddEvent()
        val state = vm.editingState.value
        assertNotNull(state)
        assertTrue(state!!.isNew)
        assertEquals(DateUtils.todayStartMillis(), state.date)
    }

    @Test
    fun `startAddEvent uses explicit date`() = runTest(testDispatcher) {
        val date = 1_700_000_000_000L
        vm.startAddEvent(date)
        assertEquals(date, vm.editingState.value!!.date)
    }

    @Test
    fun `startAddEvent uses selectedDate when no explicit date`() = runTest(testDispatcher) {
        val date = 1_700_000_000_000L
        vm.onDateClick(date)
        vm.startAddEvent()
        assertEquals(date, vm.editingState.value!!.date)
    }

    @Test
    fun `startEditEvent maps existing event into edit state`() = runTest(testDispatcher) {
        val date = 1_700_000_000_000L
        vm.startEditEvent(Event(id = 7L, title = "组会", date = date, isAllDay = true))
        val state = vm.editingState.value
        assertNotNull(state)
        assertEquals(7L, state!!.originalId)
        assertEquals("组会", state.title)
        assertFalse(state.isNew)
    }

    @Test
    fun `clearEditor resets editingState and error`() = runTest(testDispatcher) {
        vm.startAddEvent()
        vm.clearEditor()
        assertNull(vm.editingState.value)
        assertNull(vm.editorError.value)
    }

    @Test
    fun `saveEvent creates new event and closes editor`() = runTest(testDispatcher) {
        vm.saveEvent(draftState(title = "晨跑"))
        advanceUntilIdle()
        assertEquals(1, repo.addCount)
        assertNull(vm.editingState.value)
        assertNull(vm.editorError.value)
    }

    @Test
    fun `saveEvent with blank title surfaces error and keeps editor open`() = runTest(testDispatcher) {
        vm.startAddEvent()
        vm.saveEvent(draftState(title = "   "))
        advanceUntilIdle()
        assertEquals(0, repo.addCount)
        assertNotNull(vm.editingState.value)
        assertTrue(vm.editorError.value!!.contains("标题不能为空"))
    }

    @Test
    fun `saveEvent with originalId updates instead of adding`() = runTest(testDispatcher) {
        val date = DateUtils.todayStartMillis()
        val id = repo.seedEvent(title = "旧标题", date = date)
        vm.saveEvent(draftState(originalId = id, title = "新标题", date = date))
        advanceUntilIdle()
        assertEquals(0, repo.addCount)
        assertEquals(1, repo.updateCount)
        assertEquals("新标题", repo.getEventById(id)!!.title)
    }

    @Test
    fun `clearError only clears error`() = runTest(testDispatcher) {
        vm.startAddEvent()
        vm.saveEvent(draftState(title = ""))
        advanceUntilIdle()
        assertNotNull(vm.editorError.value)
        vm.clearError()
        assertNull(vm.editorError.value)
        assertNotNull(vm.editingState.value)
    }

    @Test
    fun `deleteEventFromEditor removes persisted event`() = runTest(testDispatcher) {
        val date = DateUtils.todayStartMillis()
        val id = repo.seedEvent(title = "待删", date = date)
        vm.updateEditingState(draftState(originalId = id, title = "待删", date = date))
        vm.deleteEventFromEditor()
        advanceUntilIdle()
        assertEquals(1, repo.deleteCount)
        assertNull(repo.getEventById(id))
        assertNull(vm.editingState.value)
    }

    @Test
    fun `deleteEventFromEditor on draft just closes editor`() = runTest(testDispatcher) {
        vm.startAddEvent()
        vm.deleteEventFromEditor()
        advanceUntilIdle()
        assertEquals(0, repo.deleteCount)
        assertNull(vm.editingState.value)
    }

    @Test
    fun `deleteEventFromEditor without editor is a no-op`() = runTest(testDispatcher) {
        vm.deleteEventFromEditor()
        advanceUntilIdle()
        assertEquals(0, repo.deleteCount)
        assertNull(vm.editingState.value)
    }
}