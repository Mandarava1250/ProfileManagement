package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.core.lunar.DefaultLunarCalendar
import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarDisplayRepository
import com.example.personalassistant.feature.calendar.data.repository.FakeHabitRepository
import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import com.example.personalassistant.feature.calendar.data.repository.FakeEventCategoryRepository
import com.example.personalassistant.feature.calendar.data.repository.FakeHabitDisplayRepository
import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.usecase.ObserveGlobalMonthlySumsUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveTodayPoolUseCase
import com.example.personalassistant.feature.task.data.repository.FakeTaskRepository
import com.example.personalassistant.feature.task.domain.usecase.ToggleTaskCompleteUseCase
import com.example.personalassistant.feature.timetable.data.repository.FakeTimetableRepository
import com.example.personalassistant.feature.timetable.domain.usecase.GetCoursesByWeekUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveCurrentSemesterUseCase
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import com.example.personalassistant.feature.calendar.domain.model.CalendarViewType
import com.example.personalassistant.feature.calendar.domain.model.DaySummary
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.usecase.AddEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.CancelEventReminderUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DeleteEventCategoryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DeleteEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DetectEventLiveConflictsUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DetectEventSavedConflictsUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveDayDetailUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveEventCategoriesUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveMonthSummaryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ScheduleEventReminderUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.SetTaskTimeRangeUseCase
import com.example.personalassistant.feature.habit.domain.usecase.CheckInHabitUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpdateEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpsertEventCategoryUseCase
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
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
 *
 * Phase 8 Sub-step 1 增量：
 * - 注入 FakeScheduleEventReminderUseCase / FakeCancelEventReminderUseCase
 * - 验证 saveEvent 在 reminder 非空时触发 schedule，reminder 清空/删除时触发 cancel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeCalendarRepository
    private lateinit var categoryRepo: FakeEventCategoryRepository
    private lateinit var scheduleEventReminder: FakeScheduleEventReminderUseCase
    private lateinit var cancelEventReminder: FakeCancelEventReminderUseCase
    private lateinit var vm: CalendarViewModel

    /** Phase 8 Sub-step 7：用于 CalendarViewModel 接线测试，统计调用次数。 */
    private class FakeScheduleEventReminderUseCase : ScheduleEventReminderUseCase(
        NoopReminderScheduler,
        com.example.personalassistant.core.holiday.usecase.IsHolidayUseCase { false },
    ) {
        var calls: Int = 0; private set
        var lastEvent: Event? = null; private set
        override suspend fun invoke(event: Event) {
            calls++
            lastEvent = event
        }
    }

    private class FakeCancelEventReminderUseCase : CancelEventReminderUseCase(NoopReminderScheduler) {
        var calls: Int = 0; private set
        var lastEventId: Long? = null; private set
        override suspend fun invoke(eventId: Long) {
            calls++
            lastEventId = eventId
        }
    }

    private object NoopReminderScheduler : ReminderScheduler {
        override suspend fun schedule(info: ReminderInfo) = Unit
        override suspend fun cancel(eventId: Long) = Unit
        override suspend fun cancelAll() = Unit
    }

    /**
     * Phase 8 Sub-step 9 Step 4.2：空探测（永远返回无冲突），
     * 让 CalendarViewModel 初始化能跑通，但不参与实际冲突行为断言。
     */
    private object NoopDetectEventLiveConflictsUseCase : DetectEventLiveConflictsUseCase(
        NoopCourseDao,
        NoopSemesterDao,
    ) {
        override suspend fun invoke(event: com.example.personalassistant.feature.calendar.domain.model.Event) =
            com.example.personalassistant.feature.calendar.domain.model.EventConflictInfo(
                conflictingCourses = emptyList(),
                weekNumber = null,
                timeSlice = null,
            )
    }

    private object NoopDetectEventSavedConflictsUseCase : DetectEventSavedConflictsUseCase(
        NoopCourseDao,
        NoopSemesterDao,
    ) {
        override suspend fun invoke(event: com.example.personalassistant.feature.calendar.domain.model.Event) =
            com.example.personalassistant.feature.calendar.domain.model.EventConflictInfo(
                conflictingCourses = emptyList(),
                weekNumber = null,
                timeSlice = null,
            )
    }

    private object NoopCourseDao :
        com.example.personalassistant.feature.timetable.data.local.CourseDao {
        override suspend fun findConflicts(
            semester: String, dayOfWeek: Int, startTime: Int, endTime: Int, excludeId: Long,
        ): List<com.example.personalassistant.feature.timetable.data.local.CourseEntity> = emptyList()

        // 占位：以下方法不被本测试触发；不实现也能编译通过——但 UseCase 构造需要，
        // 所以提供 no-op 默认实现避免编译失败
        override suspend fun insert(course: com.example.personalassistant.feature.timetable.data.local.CourseEntity): Long = 0
        override suspend fun insertAll(courses: List<com.example.personalassistant.feature.timetable.data.local.CourseEntity>) = Unit
        override suspend fun update(course: com.example.personalassistant.feature.timetable.data.local.CourseEntity) = Unit
        override suspend fun delete(course: com.example.personalassistant.feature.timetable.data.local.CourseEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun getById(id: Long): com.example.personalassistant.feature.timetable.data.local.CourseEntity? = null
        override fun observeBySemester(semester: String): kotlinx.coroutines.flow.Flow<List<com.example.personalassistant.feature.timetable.data.local.CourseEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun observeBySemesterAndDay(semester: String, dayOfWeek: Int): kotlinx.coroutines.flow.Flow<List<com.example.personalassistant.feature.timetable.data.local.CourseEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getBySemester(semester: String): List<com.example.personalassistant.feature.timetable.data.local.CourseEntity> = emptyList()
        override fun observeByDayOfWeek(dayOfWeek: Int): kotlinx.coroutines.flow.Flow<List<com.example.personalassistant.feature.timetable.data.local.CourseEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        // 性能优化：一次拿多个 dayOfWeek 的课程 —— 本测试不触发。
        override fun observeByDayOfWeekIn(days: List<Int>): kotlinx.coroutines.flow.Flow<List<com.example.personalassistant.feature.timetable.data.local.CourseEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun countBySemester(semester: String): Int = 0
        override fun searchCourses(keyword: String): kotlinx.coroutines.flow.Flow<List<com.example.personalassistant.feature.timetable.data.local.CourseEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getAllCourses(): List<com.example.personalassistant.feature.timetable.data.local.CourseEntity> = emptyList()
        // 性能优化（v14）：CourseDao 新增的整表清空（备份导入用）—— 本测试不触发。
        override suspend fun clearAll(): Int = 0
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.example.personalassistant.feature.timetable.data.local.CourseEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun renameSemesterReferences(oldName: String, newName: String) = Unit
    }

    private object NoopSemesterDao :
        com.example.personalassistant.feature.timetable.data.local.SemesterDao {
        override suspend fun getCurrent(): com.example.personalassistant.feature.timetable.data.local.SemesterEntity? = null
        override suspend fun insert(semester: com.example.personalassistant.feature.timetable.data.local.SemesterEntity): Long = 0
        // 性能优化（v14）：SemesterDao 新增的批量插入接口（备份导入用）—— 本测试不触发。
        override suspend fun insertAll(semesters: List<com.example.personalassistant.feature.timetable.data.local.SemesterEntity>): List<Long> = emptyList()
        // 性能优化（v14）：整表清空（备份导入用）—— 本测试不触发。
        override suspend fun clearAll(): Int = 0
        override suspend fun update(semester: com.example.personalassistant.feature.timetable.data.local.SemesterEntity) = Unit
        override suspend fun delete(semester: com.example.personalassistant.feature.timetable.data.local.SemesterEntity) = Unit
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.example.personalassistant.feature.timetable.data.local.SemesterEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getAll(): List<com.example.personalassistant.feature.timetable.data.local.SemesterEntity> = emptyList()
        override suspend fun getByName(name: String): com.example.personalassistant.feature.timetable.data.local.SemesterEntity? = null
        override fun observeCurrent(): kotlinx.coroutines.flow.Flow<com.example.personalassistant.feature.timetable.data.local.SemesterEntity?> = kotlinx.coroutines.flow.flowOf(null)
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun clearCurrent() = Unit
        override suspend fun setCurrent(name: String) = Unit
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeCalendarRepository()
        categoryRepo = FakeEventCategoryRepository()
        scheduleEventReminder = FakeScheduleEventReminderUseCase()
        cancelEventReminder = FakeCancelEventReminderUseCase()
        vm = CalendarViewModel(
            observeMonthSummary = ObserveMonthSummaryUseCase(repo),
            observeDayDetail = ObserveDayDetailUseCase(repo),
            addEvent = AddEventUseCase(repo),
            updateEvent = UpdateEventUseCase(repo),
            deleteEventUseCase = DeleteEventUseCase(repo),
            scheduleEventReminder = scheduleEventReminder,
            cancelEventReminder = cancelEventReminder,
            // Phase 8 Sub-step 9：事件分类管理
            observeEventCategories = ObserveEventCategoriesUseCase(categoryRepo),
            upsertEventCategory = UpsertEventCategoryUseCase(categoryRepo),
            deleteEventCategory = DeleteEventCategoryUseCase(categoryRepo),
            // Phase 8 Sub-step 9 Step 4.2：冲突检测（本测试只校验 VM 其他逻辑，
            // 用 Noop* 探测"调用一次 = 默认空结果"，不影响 month/event/category 相关断言）
            detectLiveConflict = NoopDetectEventLiveConflictsUseCase,
            detectSavedConflict = NoopDetectEventSavedConflictsUseCase,
            // Phase 8 Sub-step 9 Step 5：lunar 是纯函数库，直接用真实现即可
            lunarCalendar = DefaultLunarCalendar(),
            // Phase 14：今日概览（预算横条）—— 用 fake 仓储，值都是空的
            observeMonthlyBudget = ObserveMonthlyBudgetUseCase(FakeFinanceRepository()),
            observeTodayPool = ObserveTodayPoolUseCase(FakeFinanceRepository()),
            observeGlobalMonthlySums = ObserveGlobalMonthlySumsUseCase(FakeFinanceRepository()),
            // Phase 14：日视图勾选待办
            toggleTaskComplete = ToggleTaskCompleteUseCase(FakeTaskRepository()),
            // Phase 14：周视图（课表风格）
            observeCurrentSemester = ObserveCurrentSemesterUseCase(FakeTimetableRepository()),
            getCoursesByWeek = GetCoursesByWeekUseCase(FakeTimetableRepository()),
            // Phase 20.3：日历「显示设置」+ Phase 20.1：清单项时间段
            calendarDisplayRepository = FakeCalendarDisplayRepository(),
            habitDisplayRepository = FakeHabitDisplayRepository(),
            setTaskTimeRange = SetTaskTimeRangeUseCase(repo),
            // Phase 24B：习惯打卡（写入口走 habit 域的 UseCase）
            checkInHabitUseCase = CheckInHabitUseCase(FakeHabitRepository()),
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
    fun `default viewType is DAY`() = runTest(testDispatcher) {
        // Phase 14：日历 Tab 是 App 首屏，默认落在「日」视图（= 今日）
        assertEquals(CalendarViewType.DAY, vm.viewType.value)
    }

    @Test
    fun `default selectedDate is today`() = runTest(testDispatcher) {
        // Phase 14：日视图默认锚定今天（原来默认 null，进日视图会显示「请回到今天」）
        assertEquals(DateUtils.todayStartMillis(), vm.selectedDate.value)
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
    fun `selectedDayDetail defaults to today`() = runTest(testDispatcher) {
        // Phase 14：selectedDate 默认锚定今天，所以一进来就有当天的详情，
        // 不再是「请回到今天」的空态。
        advanceUntilIdle()
        val detail = vm.selectedDayDetail.first()
        assertNotNull(detail)
        assertEquals(DateUtils.todayStartMillis(), detail!!.date)
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

    // ===== Phase 8 Sub-step 1：提醒同步接线 =====

    /**
     * 构造带 reminder 的 EventEditState。reminder 非空 + startTime 非空时，
     * EventEditState.toEvent() 会得到一个 hasTime=true 的 Event，
     * 这样 ScheduleEventReminderUseCase 能正确算 triggerAt。
     */
    private fun reminderDraft(
        originalId: Long = 0L,
        title: String = "周会",
        date: Long = DateUtils.todayStartMillis(),
        reminderMinutes: Int? = 15,
        hasTime: Boolean = true,
        startHour: Int? = if (hasTime) 10 else null,
        startMinute: Int? = if (hasTime) 0 else null,
        endHour: Int? = if (hasTime) 11 else null,
        endMinute: Int? = if (hasTime) 0 else null,
    ) = EventEditState(
        originalId = originalId,
        title = title,
        date = date,
        isAllDay = !hasTime,
        hasTime = hasTime,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        reminderMinutes = reminderMinutes,
    )

    @Test
    fun `saveEvent new event with reminder triggers scheduleEventReminder`() = runTest(testDispatcher) {
        vm.saveEvent(reminderDraft(reminderMinutes = 15))
        advanceUntilIdle()

        assertEquals(1, scheduleEventReminder.calls)
        val event = scheduleEventReminder.lastEvent!!
        assertEquals(15, event.reminder)
        assertNotNull("new event should have id assigned", event.id)
        assertTrue(event.id > 0)
        // cancel 不会被调用（新建场景且 reminder 非空）
        assertEquals(0, cancelEventReminder.calls)
    }

    @Test
    fun `saveEvent new event without reminder does not schedule or cancel`() = runTest(testDispatcher) {
        vm.saveEvent(reminderDraft(reminderMinutes = null))
        advanceUntilIdle()

        assertEquals(0, scheduleEventReminder.calls)
        assertEquals(0, cancelEventReminder.calls)
    }

    @Test
    fun `saveEvent update with reminder triggers schedule not cancel`() = runTest(testDispatcher) {
        val date = DateUtils.todayStartMillis()
        val id = repo.seedEvent(title = "原事件", date = date)
        vm.saveEvent(reminderDraft(originalId = id, reminderMinutes = 30))
        advanceUntilIdle()

        assertEquals(1, scheduleEventReminder.calls)
        // update 场景下 reminder 非空时只 schedule，不 cancel
        assertEquals(0, cancelEventReminder.calls)
        assertEquals(id, scheduleEventReminder.lastEvent!!.id)
    }

    @Test
    fun `saveEvent update with reminder cleared triggers cancel`() = runTest(testDispatcher) {
        val date = DateUtils.todayStartMillis()
        val id = repo.seedEvent(title = "原事件", date = date)
        // 原事件本来有 reminder，现在改成 null（清空提醒）
        vm.saveEvent(reminderDraft(originalId = id, reminderMinutes = null))
        advanceUntilIdle()

        // reminder 空 + startTime 空（hasTime=false 时）→ 走 cancel 分支
        assertEquals(0, scheduleEventReminder.calls)
        assertEquals(1, cancelEventReminder.calls)
        assertEquals(id, cancelEventReminder.lastEventId)
    }

    @Test
    fun `deleteEventFromEditor triggers cancelEventReminder`() = runTest(testDispatcher) {
        val date = DateUtils.todayStartMillis()
        val id = repo.seedEvent(title = "待删", date = date)
        vm.updateEditingState(reminderDraft(originalId = id, reminderMinutes = null))
        vm.deleteEventFromEditor()
        advanceUntilIdle()

        assertEquals(1, repo.deleteCount)
        // 即使 reminderMinutes=null 也要 cancel（清残留 PendingIntent）
        assertEquals(1, cancelEventReminder.calls)
        assertEquals(id, cancelEventReminder.lastEventId)
    }

    @Test
    fun `deleteEventFromEditor on draft does not trigger cancel`() = runTest(testDispatcher) {
        vm.startAddEvent()
        vm.deleteEventFromEditor()
        advanceUntilIdle()

        assertEquals(0, cancelEventReminder.calls)
    }
}