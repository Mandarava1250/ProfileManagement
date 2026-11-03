package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.core.holiday.usecase.IsHolidayUseCase
import com.example.personalassistant.core.lunar.DefaultLunarCalendar
import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarDisplayRepository
import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import com.example.personalassistant.feature.calendar.data.repository.FakeEventCategoryRepository
import com.example.personalassistant.feature.calendar.data.repository.FakeHabitDisplayRepository
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import com.example.personalassistant.feature.calendar.domain.usecase.AddEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.CancelEventReminderUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DeleteEventCategoryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DeleteEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DetectEventLiveConflictsUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DetectEventSavedConflictsUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveDayDetailUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveEventCategoriesUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveMonthEventsUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveMonthSummaryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ScheduleEventReminderUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.SetTaskTimeRangeUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpdateEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpsertEventCategoryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.fake.FakeCourseDao
import com.example.personalassistant.feature.calendar.domain.usecase.fake.FakeSemesterDao
import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.usecase.ObserveGlobalMonthlySumsUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveTodayPoolUseCase
import com.example.personalassistant.feature.task.data.repository.FakeTaskRepository
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import com.example.personalassistant.feature.task.domain.usecase.ToggleTaskCompleteUseCase
import com.example.personalassistant.feature.timetable.data.repository.FakeTimetableRepository
import com.example.personalassistant.feature.timetable.domain.usecase.GetCoursesByWeekUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveCurrentSemesterUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 14 阶段七：周视图时间线单测（纯 JVM）
 *
 * 被测对象：`feature/calendar/presentation/CalendarViewModel.kt` 的
 * [CalendarViewModel.weekTimeline]（内部走 observeWeekTimeline）。
 *
 * 覆盖：
 * - 周视图固定 7 列（周一 ~ 周日），列日期 = 本周起点 + offset 天
 * - **任务只出现在自己那一天那一列**（Phase 14 修掉的 bug：任务全堆在一列里）
 * - 跨天任务（同一个任务落在多天）在每一天各出现一次，不污染其它列
 * - 已完成任务仍出现在周视图里（VM 不二次过滤，过滤是数据层的事）
 * - 事件按日期落到对应列
 *
 * Fake 优先：不引 Mockito/MockK，用内存版 [FakeCalendarRepository] +
 * 一个接口委托的包装，把「某天有哪些任务」喂进去。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeekTimelineTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var eventRepo: FakeCalendarRepository
    private lateinit var repo: TaskAwareCalendarRepository
    private lateinit var vm: CalendarViewModel

    // ===== 固定基准周：2024-06-10 是周一（不依赖「今天」，结果不随日期漂移）=====

    private val anchorMonday: Long = localStartOfDay(LocalDate.of(2024, 6, 10))

    /** 本周起点 —— 直接用 VM 自己的口径，避免测试和实现各算各的 */
    private val weekStart: Long = CalendarViewModel.computeWeekRange(anchorMonday).first

    /** 第 offset 天（周一=0 … 周日=6）的日期，与 VM 里 dayDate 的算法一致 */
    private fun dayDate(offset: Int): Long = weekStart + offset * WeekTimeline.ONE_DAY_MILLIS

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        eventRepo = FakeCalendarRepository()
        repo = TaskAwareCalendarRepository(eventRepo)
        vm = buildViewModel(repo)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ===== 列结构 =====

    @Test
    fun `week timeline exposes seven columns from monday to sunday`() = runTest(testDispatcher) {
        vm.onDateClick(anchorMonday)
        val timeline = vm.weekTimeline.first { it.days.size == WeekTimeline.DAY_COUNT }

        assertEquals(WeekTimeline.DAY_COUNT, timeline.days.size)
        assertEquals(weekStart, timeline.weekStart)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), timeline.days.map { it.dayOfWeek })
        timeline.days.forEachIndexed { offset, column ->
            assertEquals(dayDate(offset), column.date)
        }
    }

    // ===== 任务归属 =====

    @Test
    fun `task only shows up in the column of its own day`() = runTest(testDispatcher) {
        repo.setTasks(
            mapOf(
                dayDate(0) to listOf(Task(id = 1L, title = "周一任务", dueDate = dayDate(0))),
                dayDate(2) to listOf(Task(id = 2L, title = "周三任务", dueDate = dayDate(2))),
            )
        )
        vm.onDateClick(anchorMonday)
        val timeline = vm.weekTimeline.first { it.days.size == WeekTimeline.DAY_COUNT }

        assertEquals(listOf("周一任务"), timeline.days[0].tasks.map { it.title })
        assertTrue("周二这一列不该有任务", timeline.days[1].tasks.isEmpty())
        assertEquals(listOf("周三任务"), timeline.days[2].tasks.map { it.title })
        assertTrue("周四这一列不该有任务", timeline.days[3].tasks.isEmpty())
        assertTrue("周五这一列不该有任务", timeline.days[4].tasks.isEmpty())
        assertTrue("周六这一列不该有任务", timeline.days[5].tasks.isEmpty())
        assertTrue("周日这一列不该有任务", timeline.days[6].tasks.isEmpty())
        // 任务没有在别的列里重复出现
        assertEquals(2, timeline.days.flatMap { it.tasks }.size)
    }

    @Test
    fun `task spanning several days shows up once on each of its days`() = runTest(testDispatcher) {
        // 同一个任务（同 id）落在周一和周四 —— 典型的跨天任务
        val spanning = Task(id = 9L, title = "跨天任务", dueDate = dayDate(0))
        repo.setTasks(
            mapOf(
                dayDate(0) to listOf(spanning),
                dayDate(3) to listOf(spanning),
            )
        )
        vm.onDateClick(anchorMonday)
        val timeline = vm.weekTimeline.first { it.days.size == WeekTimeline.DAY_COUNT }

        assertEquals(listOf("跨天任务"), timeline.days[0].tasks.map { it.title })
        assertEquals(listOf("跨天任务"), timeline.days[3].tasks.map { it.title })
        assertTrue("周二这一列不该有任务", timeline.days[1].tasks.isEmpty())
        assertTrue("周三这一列不该有任务", timeline.days[2].tasks.isEmpty())
        assertTrue("周五这一列不该有任务", timeline.days[4].tasks.isEmpty())
        assertTrue("周六这一列不该有任务", timeline.days[5].tasks.isEmpty())
        assertTrue("周日这一列不该有任务", timeline.days[6].tasks.isEmpty())
        assertEquals(2, timeline.days.flatMap { it.tasks }.size)
    }

    @Test
    fun `completed task is not dropped from the week view`() = runTest(testDispatcher) {
        // 「已完成要不要显示」是数据层的口径；VM 这一层只负责不把它悄悄丢掉
        repo.setTasks(
            mapOf(
                dayDate(2) to listOf(
                    Task(id = 3L, title = "未完成", dueDate = dayDate(2)),
                    Task(id = 4L, title = "已完成", dueDate = dayDate(2), isCompleted = true),
                )
            )
        )
        vm.onDateClick(anchorMonday)
        val timeline = vm.weekTimeline.first { it.days.size == WeekTimeline.DAY_COUNT }

        val titles = timeline.days[2].tasks.map { it.title }
        assertTrue("已完成的任务不该被 VM 丢掉：$titles", titles.contains("已完成"))
        assertTrue("未完成的任务也要在：$titles", titles.contains("未完成"))
        assertTrue(
            "已完成标记要原样带到周视图",
            timeline.days[2].tasks.first { it.title == "已完成" }.isCompleted,
        )
        assertTrue("别的列不该冒出来", timeline.days[0].tasks.isEmpty())
    }

    // ===== 事件归属 =====

    @Test
    fun `event falls into the column of its own date`() = runTest(testDispatcher) {
        val eventId = eventRepo.seedEvent(title = "周三会议", date = dayDate(2))
        vm.onDateClick(anchorMonday)
        val timeline = vm.weekTimeline.first { it.days.size == WeekTimeline.DAY_COUNT }

        val owners = timeline.days.filter { column -> column.events.any { it.id == eventId } }
        assertEquals("事件只能落在一列里", 1, owners.size)
        assertEquals(dayDate(2), owners.single().date)
        assertEquals(3, owners.single().dayOfWeek)
        assertTrue("周一这一列不该有事件", timeline.days[0].events.isEmpty())
        assertTrue("周二这一列不该有事件", timeline.days[1].events.isEmpty())
        assertTrue("周四这一列不该有事件", timeline.days[3].events.isEmpty())
    }

    // ===== 组装 =====

    private fun buildViewModel(calendarRepository: CalendarRepository): CalendarViewModel {
        val categoryRepo = FakeEventCategoryRepository()
        val courseDao = FakeCourseDao()
        val semesterDao = FakeSemesterDao()
        return CalendarViewModel(
            observeMonthSummary = ObserveMonthSummaryUseCase(calendarRepository),
            observeDayDetail = ObserveDayDetailUseCase(calendarRepository),
            addEvent = AddEventUseCase(calendarRepository),
            updateEvent = UpdateEventUseCase(calendarRepository),
            deleteEventUseCase = DeleteEventUseCase(calendarRepository),
            scheduleEventReminder = ScheduleEventReminderUseCase(
                NoopReminderScheduler,
                IsHolidayUseCase { false },
            ),
            cancelEventReminder = CancelEventReminderUseCase(NoopReminderScheduler),
            observeEventCategories = ObserveEventCategoriesUseCase(categoryRepo),
            upsertEventCategory = UpsertEventCategoryUseCase(categoryRepo),
            deleteEventCategory = DeleteEventCategoryUseCase(categoryRepo),
            detectLiveConflict = DetectEventLiveConflictsUseCase(courseDao, semesterDao),
            detectSavedConflict = DetectEventSavedConflictsUseCase(courseDao, semesterDao),
            lunarCalendar = DefaultLunarCalendar(),
            observeMonthlyBudget = ObserveMonthlyBudgetUseCase(FakeFinanceRepository()),
            observeTodayPool = ObserveTodayPoolUseCase(FakeFinanceRepository()),
            observeGlobalMonthlySums = ObserveGlobalMonthlySumsUseCase(FakeFinanceRepository()),
            toggleTaskComplete = ToggleTaskCompleteUseCase(FakeTaskRepository()),
            observeCurrentSemester = ObserveCurrentSemesterUseCase(FakeTimetableRepository()),
            getCoursesByWeek = GetCoursesByWeekUseCase(FakeTimetableRepository()),
            // Phase 20.3：日历「显示设置」+ Phase 20.1：清单项时间段
            calendarDisplayRepository = FakeCalendarDisplayRepository(),
            habitDisplayRepository = FakeHabitDisplayRepository(),
            setTaskTimeRange = SetTaskTimeRangeUseCase(calendarRepository),
        )
    }

    private object NoopReminderScheduler : ReminderScheduler {
        override suspend fun schedule(info: ReminderInfo) = Unit
        override suspend fun cancel(eventId: Long) = Unit
        override suspend fun cancelAll() = Unit
    }

    /**
     * 带任务的日历仓库 Fake
     *
     * [FakeCalendarRepository] 是 final 类，不能继承 —— 这里用「接口委托」包一层，
     * 只把 observeDayDetail 换成「按日期给任务」的版本，其余行为原样转发。
     */
    private class TaskAwareCalendarRepository(
        base: FakeCalendarRepository,
    ) : CalendarRepository by base {

        private val baseRepo = base

        private val tasksByDate = MutableStateFlow<Map<Long, List<Task>>>(emptyMap())

        /** 设置「哪一天有哪些任务」 */
        fun setTasks(tasks: Map<Long, List<Task>>) {
            tasksByDate.value = tasks
        }

        override fun observeDayDetail(date: Long): Flow<DayDetail> =
            combine(baseRepo.observeDayDetail(date), tasksByDate) { detail, tasks ->
                detail.copy(tasks = tasks[date].orEmpty())
            }
    }

    private fun localStartOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
