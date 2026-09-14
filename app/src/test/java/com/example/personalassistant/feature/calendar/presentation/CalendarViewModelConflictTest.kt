package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.core.lunar.DefaultLunarCalendar
import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import com.example.personalassistant.feature.calendar.data.repository.FakeEventCategoryRepository
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.EventConflictInfo
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
import com.example.personalassistant.feature.calendar.domain.usecase.UpdateEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpsertEventCategoryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.fake.FakeCourseDao
import com.example.personalassistant.feature.calendar.domain.usecase.fake.FakeSemesterDao
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Phase 8 Sub-step 9 Step 4.2：CalendarViewModel 冲突检测相关测试
 *
 * ## 覆盖
 * - liveConflictInfo 流的 5 个用例（关闭、整天、学期内、学期外、debounce 节流）
 * - savedConflictsByEventId 流的 3 个用例（3 事件、学期外、跟随变化更新）
 *
 * ## 工具
 * - 用真实的 [DetectEventLiveConflictsUseCase] / [DetectEventSavedConflictsUseCase]
 *   + [FakeCourseDao] / [FakeSemesterDao]
 * - 用 [CountingDetectLiveConflictsUseCase] 记录调用次数以验证 debounce 节流
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelConflictTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeCalendarRepository
    private lateinit var categoryRepo: FakeEventCategoryRepository
    private lateinit var courseDao: FakeCourseDao
    private lateinit var semesterDao: FakeSemesterDao
    private lateinit var detectLiveConflict: CountingDetectLiveConflictsUseCase
    private lateinit var detectSavedConflict: DetectEventSavedConflictsUseCase
    private lateinit var scheduleEventReminder: FakeScheduleEventReminderUseCase
    private lateinit var cancelEventReminder: FakeCancelEventReminderUseCase
    private lateinit var vm: CalendarViewModel

    private val DAY = 24L * 60 * 60 * 1000

    // 锚 2026-09-07 周一（第 1 周）
    private val semesterStart: Long =
        LocalDateTime.of(2026, 9, 7, 0, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val semesterEnd: Long = semesterStart + 18L * 7 * DAY
    private val semesterName = "2026-Fall"

    /** 构造一条 monday 9:00–10:00 的 Event（学期内第 1 周） */
    private fun mondayEvent(
        id: Long = 0L,
        title: String = "测试事件",
        isAllDay: Boolean = false,
        startHour: Int = 9,
        startMinute: Int = 0,
        endHour: Int = 10,
        endMinute: Int = 0,
    ): Event {
        val start = semesterStart + startHour * 3600_000L + startMinute * 60_000L
        val end = semesterStart + endHour * 3600_000L + endMinute * 60_000L
        return Event(
            id = id,
            title = title,
            date = semesterStart,
            isAllDay = isAllDay,
            startTime = if (isAllDay) null else start,
            endTime = if (isAllDay) null else end,
        )
    }

    private fun courseEntity(
        id: Long = 1L,
        name: String = "高数",
        dayOfWeek: Int = 1,
        startMin: Int = 540,  // 9:00
        endMin: Int = 600,    // 10:00
        semester: String = semesterName,
        weeks: String? = null,
    ) = CourseEntity(
        id = id,
        name = name,
        dayOfWeek = dayOfWeek,
        startTime = startMin,
        endTime = endMin,
        color = 0xFFE57373.toInt(),
        semester = semester,
        weeks = weeks,
    )

    private fun semesterEntity(): SemesterEntity = SemesterEntity(
        name = semesterName,
        startDate = semesterStart,
        endDate = semesterEnd,
        isCurrent = true,
    )

    /**
     * 包装 [DetectEventLiveConflictsUseCase]，记录 invoke 调用次数
     * —— 用于验证 debounce(150ms) 节流效果
     */
    private class CountingDetectLiveConflictsUseCase(
        courseDao: CourseDao,
        semesterDao: SemesterDao,
    ) : DetectEventLiveConflictsUseCase(courseDao, semesterDao) {
        var calls: Int = 0; private set
        override suspend fun invoke(event: Event): EventConflictInfo {
            calls++
            return super.invoke(event)
        }
    }

    private class FakeScheduleEventReminderUseCase : ScheduleEventReminderUseCase(
        NoopReminderScheduler,
        com.example.personalassistant.core.holiday.usecase.IsHolidayUseCase { false },
    ) {
        override suspend fun invoke(event: Event) = Unit
    }

    private class FakeCancelEventReminderUseCase : CancelEventReminderUseCase(NoopReminderScheduler) {
        override suspend fun invoke(eventId: Long) = Unit
    }

    private object NoopReminderScheduler : ReminderScheduler {
        override suspend fun schedule(info: ReminderInfo) = Unit
        override suspend fun cancel(eventId: Long) = Unit
        override suspend fun cancelAll() = Unit
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeCalendarRepository()
        categoryRepo = FakeEventCategoryRepository()
        courseDao = FakeCourseDao()
        semesterDao = FakeSemesterDao(current = semesterEntity())
        detectLiveConflict = CountingDetectLiveConflictsUseCase(courseDao, semesterDao)
        detectSavedConflict = DetectEventSavedConflictsUseCase(courseDao, semesterDao)
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
            observeEventCategories = ObserveEventCategoriesUseCase(categoryRepo),
            upsertEventCategory = UpsertEventCategoryUseCase(categoryRepo),
            deleteEventCategory = DeleteEventCategoryUseCase(categoryRepo),
            detectLiveConflict = detectLiveConflict,
            detectSavedConflict = detectSavedConflict,
            // Phase 8 Sub-step 9 Step 5：lunar 是纯函数库，直接用真实现
            lunarCalendar = DefaultLunarCalendar(),
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ===================================================================
    // ===== liveConflictInfo 流的 5 个用例 =====
    // ===================================================================

    /**
     * 用例 1：`_editingState=null` → `liveConflictInfo=null`
     * 默认状态下没有编辑器，没有冲突 → 流保持初始 null
     */
    @Test
    fun `liveConflictInfo is null when editor is closed`() = runTest(testDispatcher) {
        assertNull(vm.liveConflictInfo.value)
    }

    /**
     * 用例 2：`_editingState=newOnDate` + 学期内有课程冲突 → liveConflictInfo 含课程列表
     * 准备：
     * - courseDao seed 一条 monday 9:00-10:00 的高数
     * - editor 打开一个 9:00-10:00 的新事件
     * 断言：
     * - debounce 后 liveConflictInfo.conflictingCourses.size == 1
     * - weekNumber == 1（学期第一周）
     */
    @Test
    fun `liveConflictInfo reflects conflicts when editor opens with overlap`() = runTest(testDispatcher) {
        courseDao.seed(courseEntity(name = "高数", dayOfWeek = 1, startMin = 540, endMin = 600))
        vm.startAddEvent(date = semesterStart) // 默认 9:00–10:00，title="" 是草稿态
        vm.updateEditingState(vm.editingState.value!!.copy(title = "晨读高数")) // title 非空才触发 detect
        // 等待 debounce(150ms) 触发
        advanceTimeBy(200L)
        runCurrent()
        val info = vm.liveConflictInfo.value
        assertNotNull("expected liveConflictInfo to be emitted", info)
        assertEquals(1, info!!.conflictingCourses.size)
        assertEquals("高数", info.conflictingCourses.first().name)
        assertEquals(1, info.weekNumber)
        assertTrue(info.isWithinSemester)
        assertTrue(info.hasConflict)
    }

    /**
     * 用例 3：`_editingState=整天事件` → liveConflictInfo.isAllDay=true，conflictingCourses=empty
     * 整天事件没有 TimeSlice，直接短路返回 null timeSlice
     */
    @Test
    fun `liveConflictInfo isAllDay true for all-day event`() = runTest(testDispatcher) {
        val allDay = EventEditState.newOnDate(semesterStart).copy(
            title = "全天讲座",  // 非空触发 detect；toEvent() 时 isAllDay=true 让 start/end = null
            isAllDay = true,
            hasTime = false,
            startHour = null,
            startMinute = null,
            endHour = null,
            endMinute = null,
        )
        vm.updateEditingState(allDay)
        advanceTimeBy(200L)
        runCurrent()
        val info = vm.liveConflictInfo.value
        assertNotNull(info)
        assertTrue(info!!.isAllDay)
        assertTrue(info.conflictingCourses.isEmpty())
        assertFalse(info.hasConflict)
        assertNull(info.timeSlice)
    }

    /**
     * 用例 4：debounce(150ms) 节流生效
     * 连续触发 5 次 editingState 变化，只触发 1 次 detect
     */
    @Test
    fun `debounce coalesces rapid editingState changes into one detect call`() = runTest(testDispatcher) {
        courseDao.seed(courseEntity(name = "高数"))
        repeat(5) { i ->
            vm.updateEditingState(
                EventEditState.newOnDate(semesterStart).copy(title = "edit-$i"),
            )
            // 每次间隔 30ms（< 150ms），debounce 不应触发
            advanceTimeBy(30L)
            runCurrent()
        }
        // 总经过 5 × 30 = 150ms，最后一次 30ms 还没过完 → 没触发
        assertEquals(
            "detect should not have fired while within debounce window",
            0,
            detectLiveConflict.calls,
        )
        // 再 advance 200ms，触发 debounce
        advanceTimeBy(200L)
        runCurrent()
        assertEquals(
            "detect should fire exactly once after debounce window",
            1,
            detectLiveConflict.calls,
        )
    }

    /**
     * 用例 5：学期外（date 早于 startDate） → liveConflictInfo.conflictingCourses=empty
     * 验证 WeekCalculator 学期外短路
     */
    @Test
    fun `liveConflictInfo empty when event date is outside semester`() = runTest(testDispatcher) {
        courseDao.seed(courseEntity(name = "高数"))
        // 学期前一天（早于 startDate）
        val beforeSemester = semesterStart - DAY
        vm.startAddEvent(date = beforeSemester)
        vm.updateEditingState(vm.editingState.value!!.copy(title = "假期活动"))
        advanceTimeBy(200L)
        runCurrent()
        val info = vm.liveConflictInfo.value
        assertNotNull(info)
        assertTrue(info!!.conflictingCourses.isEmpty())
        assertFalse(info.hasConflict)
        assertNull("weekNumber should be null when outside semester", info.weekNumber)
    }

    // ===================================================================
    // ===== savedConflictsByEventId 流的 3 个用例 =====
    // ===================================================================

    /**
     * 用例 6：selectedDayDetail.events=3 个 + weekNumber=1 → savedConflictsByEventId.size=3
     * 即使所有 event 都没有冲突（empty conflict），也会被加入 map（key 存在）
     */
    @Test
    fun `savedConflictsByEventId covers all 3 events when weekNumber present`() = runTest(testDispatcher) {
        // 学期内第 1 周，让 seedEvent id 从 1 起
        repo.seedEvent(date = semesterStart, isAllDay = true, id = 1L, title = "event-1")
        repo.seedEvent(date = semesterStart, isAllDay = true, id = 2L, title = "event-2")
        repo.seedEvent(date = semesterStart, isAllDay = true, id = 3L, title = "event-3")
        repo.weekNumberForTest = 1
        // 触发 selectedDayDetail
        vm.onDateClick(semesterStart)
        advanceUntilIdle()
        val map = vm.savedConflictsByEventId.first { it.size == 3 || it.isEmpty() }
        assertEquals("3 events should produce 3 conflict entries", 3, map.size)
        assertTrue(map.containsKey(1L))
        assertTrue(map.containsKey(2L))
        assertTrue(map.containsKey(3L))
    }

    /**
     * 用例 7：selectedDayDetail.weekNumber=null → savedConflictsByEventId=emptyMap
     * 学期外 / 无学期短路
     */
    @Test
    fun `savedConflictsByEventId is empty when weekNumber is null`() = runTest(testDispatcher) {
        repo.seedEvent(date = semesterStart, isAllDay = true, id = 1L, title = "event-1")
        // weekNumberForTest 默认 null
        vm.onDateClick(semesterStart)
        advanceUntilIdle()
        // selectedDayDetail 已 emit，但 saved 短路
        assertTrue(vm.savedConflictsByEventId.value.isEmpty())
    }

    /**
     * 用例 8：savedConflictsByEventId 跟随 selectedDayDetail 变化更新（不卡旧值）
     * 切换到不同日期 / 切换 weekNumber → map 内容更新
     */
    @Test
    fun `savedConflictsByEventId updates when selectedDayDetail changes`() = runTest(testDispatcher) {
        // day 1: weekNumber=null → emptyMap
        repo.seedEvent(date = semesterStart, isAllDay = true, id = 1L, title = "day1-event")
        vm.onDateClick(semesterStart)
        advanceUntilIdle()
        val firstMap = vm.savedConflictsByEventId.value
        assertTrue("day1 (weekNumber=null) should yield empty map", firstMap.isEmpty())

        // day 2: same date, weekNumber=1 → map 含 event 1
        repo.weekNumberForTest = 1
        // observeDayDetail 是 StateFlow-derived，触发新的 emission 需要状态变化
        // FakeCalendarRepository 在 eventsState 不变时也可能不重发 → 我们直接换 eventsState
        repo.clear()
        repo.seedEvent(date = semesterStart, isAllDay = true, id = 1L, title = "day2-event")
        advanceUntilIdle()
        val secondMap = vm.savedConflictsByEventId.first { it.isNotEmpty() || it.isEmpty() }
        // 等到 map 非空或稳定
        val stableMap = if (secondMap.isEmpty()) {
            vm.savedConflictsByEventId.first { it.isNotEmpty() }
        } else secondMap
        assertTrue("day2 (weekNumber=1) should contain event", stableMap.containsKey(1L))
        // map 跟 day1 不一样，证明确实在更新
        assertTrue(stableMap != firstMap)
    }
}