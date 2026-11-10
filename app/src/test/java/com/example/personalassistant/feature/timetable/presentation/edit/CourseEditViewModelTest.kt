package com.example.personalassistant.feature.timetable.presentation.edit

import com.example.personalassistant.feature.timetable.data.repository.FakeTimetableRepository
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import com.example.personalassistant.feature.timetable.domain.model.Period
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule
import com.example.personalassistant.feature.timetable.domain.repository.TimeSlotRepository
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveTimeSlotScheduleUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.UpsertCourseUseCase
import com.example.personalassistant.service.reminder.CourseReminderRescheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 课程编辑页 ViewModel 测试（Phase 28 P8：一门课多课时）
 *
 * 覆盖：
 * - 课时的增 / 删 / 改
 * - 不能删到最后一条课时
 * - mode 反推（开始时刻命中任一 period 的 startMinutes → PERIOD）
 * - 保存整门课**一次性**提交
 * - 冲突定位到具体课时（错误回写到那一行）
 * - 保存成功后重排课程提醒闹钟
 *
 * 不引入 mockk：仓储用现成的 [FakeTimetableRepository]，其余手写 fake。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CourseEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** 只用来「记录有没有被调用」的假提醒重排器 */
    private class FakeCourseReminderRescheduler : CourseReminderRescheduler {
        var callCount: Int = 0
            private set

        /**
         * Phase 28 P9：最近一次重排用的是**哪个学期**
         *
         * null = 没传学期（走当前学期）
         */
        var lastSemesterName: String? = null
            private set

        override suspend fun rescheduleAll(semesterName: String?) {
            callCount++
            lastSemesterName = semesterName
        }
    }

    /** 固定一张课时表的假课时表仓储 */
    private class FakeTimeSlotRepository(
        private val schedule: TimeSlotSchedule,
    ) : TimeSlotRepository {
        override fun observeSchedule(): Flow<TimeSlotSchedule> = flowOf(schedule)
        override suspend fun saveSchedule(schedule: TimeSlotSchedule) = Unit
        override suspend fun resetToDefault() = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ===== 测试脚手架 =====

    /** 3 节课的课时表：08:00-08:45 / 09:00-09:45 / 10:00-10:45 */
    private val schedule = TimeSlotSchedule(
        listOf(
            Period(1, 8 * 60, 8 * 60 + 45),
            Period(2, 9 * 60, 9 * 60 + 45),
            Period(3, 10 * 60, 10 * 60 + 45),
        ),
    )

    private val semester = Semester(
        name = "2024-2025-1",
        startDate = 0L,
        endDate = 100L,
        isCurrent = true,
    )

    private fun course(
        id: Long = 0L,
        name: String = "高数",
        dayOfWeek: Int = 1,
        startTime: Int = 8 * 60,
        endTime: Int = 8 * 60 + 45,
        semesterName: String? = "2024-2025-1",
        teacher: String = "",
        location: String = "",
    ) = Course(
        id = id,
        name = name,
        teacher = teacher,
        location = location,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        color = 0xFFE57373.toInt(),
        semester = semesterName,
    )

    private suspend fun newRepository(): FakeTimetableRepository =
        FakeTimetableRepository().also { it.seedSemester(semester) }

    private fun viewModel(
        repository: FakeTimetableRepository,
        rescheduler: FakeCourseReminderRescheduler = FakeCourseReminderRescheduler(),
    ) = CourseEditViewModel(
        repository = repository,
        upsertCourseUseCase = UpsertCourseUseCase(repository),
        observeTimeSlotSchedule = ObserveTimeSlotScheduleUseCase(FakeTimeSlotRepository(schedule)),
        reminderRescheduler = rescheduler,
    )

    /**
     * 拿到第 [index] 个课时的**本地 key**（Phase 28 P9）
     *
     * 编辑页按 key 定位课时，不按下标 —— 下标会随删除而错位。
     */
    private fun CourseEditViewModel.keyAt(index: Int): Long =
        uiState.value.sessions[index].localKey

    // ===== 课时增删改 =====

    @Test
    fun `新建课程默认带一个周一第1节的课时`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)

        vm.initialize(0L)
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        assertEquals(1, sessions.size)
        val first = sessions[0]
        assertEquals(1, first.dayOfWeek)
        assertEquals(TimeMode.PERIOD, first.mode)
        assertEquals(1, first.startPeriodIndex)
        assertEquals(8 * 60, first.startMinutes)
        assertEquals(9 * 60 + 45, first.endMinutes)
    }

    @Test
    fun `添加课时追加一条并自动展开`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()

        vm.addSession()
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        assertEquals(2, sessions.size)
        assertTrue("新加的课时应该自动展开", sessions[1].expanded)
        assertEquals(1, sessions[1].dayOfWeek)
    }

    @Test
    fun `改课时只影响那一行`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.addSession()
        advanceUntilIdle()

        vm.updateSessionDay(vm.keyAt(1), 3)
        vm.updateSessionStartMinutes(vm.keyAt(1), 10 * 60)
        vm.updateSessionEndMinutes(vm.keyAt(1), 10 * 60 + 45)
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        // 第一行没被动过
        assertEquals(1, sessions[0].dayOfWeek)
        assertEquals(8 * 60, sessions[0].startMinutes)
        // 第二行改了
        assertEquals(3, sessions[1].dayOfWeek)
        assertEquals(10 * 60, sessions[1].startMinutes)
        assertEquals(10 * 60 + 45, sessions[1].endMinutes)
    }

    @Test
    fun `选第N节会把分钟数同步写回`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()

        vm.updateSessionStartPeriod(vm.keyAt(0), 3)
        vm.updateSessionEndPeriod(vm.keyAt(0), 3)
        advanceUntilIdle()

        val session = vm.uiState.value.sessions[0]
        assertEquals(TimeMode.PERIOD, session.mode)
        assertEquals(3, session.startPeriodIndex)
        assertEquals(3, session.endPeriodIndex)
        assertEquals(10 * 60, session.startMinutes)
        assertEquals(10 * 60 + 45, session.endMinutes)
    }

    @Test
    fun `结束时间早于开始时间时行内报错`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()

        vm.updateSessionEndMinutes(vm.keyAt(0), 8 * 60)
        advanceUntilIdle()

        val session = vm.uiState.value.sessions[0]
        assertNotNull(session.errorMessage)
        assertTrue(session.errorMessage!!.contains("结束时间"))
        assertTrue("非法时间时整门课不能保存", !vm.uiState.value.isValid)
    }

    @Test
    fun `删除课时`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.addSession()
        advanceUntilIdle()

        vm.removeSession(vm.keyAt(1))
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.sessions.size)
    }

    @Test
    fun `不能删到最后一条课时`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()

        vm.removeSession(vm.keyAt(0))
        advanceUntilIdle()

        assertEquals("最后一条课时不能被删掉", 1, vm.uiState.value.sessions.size)
    }

    // ===== mode 反推 =====

    @Test
    fun `开始时刻命中某一节就用 PERIOD 模式`() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.seedCourse(course(id = 10L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45))
        repo.seedSessions(
            10L,
            listOf(CourseSession(courseId = 10L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45)),
        )
        val vm = viewModel(repo)

        vm.initialize(10L)
        advanceUntilIdle()

        val session = vm.uiState.value.sessions[0]
        assertEquals(TimeMode.PERIOD, session.mode)
        assertEquals(1, session.startPeriodIndex)
        assertEquals(1, session.endPeriodIndex)
    }

    @Test
    fun `开始时刻没命中任何一节就用 CLOCK 模式`() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.seedCourse(course(id = 10L, dayOfWeek = 2, startTime = 8 * 60 + 10, endTime = 9 * 60))
        repo.seedSessions(
            10L,
            listOf(CourseSession(courseId = 10L, dayOfWeek = 2, startTime = 8 * 60 + 10, endTime = 9 * 60)),
        )
        val vm = viewModel(repo)

        vm.initialize(10L)
        advanceUntilIdle()

        assertEquals(TimeMode.CLOCK, vm.uiState.value.sessions[0].mode)
    }

    @Test
    fun `开始命中但结束没命中的课时也判为 PERIOD 且分钟数原样保留`() = runTest(testDispatcher) {
        // 08:00 命中第 1 节的开始；10:00 不落在任何一节的结束点上
        val repo = newRepository()
        repo.seedCourse(course(id = 10L, dayOfWeek = 3, startTime = 8 * 60, endTime = 10 * 60))
        repo.seedSessions(
            10L,
            listOf(CourseSession(courseId = 10L, dayOfWeek = 3, startTime = 8 * 60, endTime = 10 * 60)),
        )
        val vm = viewModel(repo)

        vm.initialize(10L)
        advanceUntilIdle()

        val session = vm.uiState.value.sessions[0]
        assertEquals(TimeMode.PERIOD, session.mode)
        // 分钟数不被节表改写（否则保存时结束时间会被悄悄改掉）
        assertEquals(8 * 60, session.startMinutes)
        assertEquals(10 * 60, session.endMinutes)
    }

    @Test
    fun `每个课时单独记自己的模式`() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.seedCourse(course(id = 10L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45))
        repo.seedSessions(
            10L,
            listOf(
                CourseSession(courseId = 10L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45),
                CourseSession(courseId = 10L, dayOfWeek = 2, startTime = 8 * 60 + 10, endTime = 9 * 60),
            ),
        )
        val vm = viewModel(repo)

        vm.initialize(10L)
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        assertEquals(2, sessions.size)
        assertEquals(TimeMode.PERIOD, sessions[0].mode)
        assertEquals(TimeMode.CLOCK, sessions[1].mode)
    }

    // ===== 保存 =====

    @Test
    fun `保存时整门课一次性提交`() = runTest(testDispatcher) {
        val repo = newRepository()
        val rescheduler = FakeCourseReminderRescheduler()
        val vm = viewModel(repo, rescheduler)
        vm.initialize(0L)
        advanceUntilIdle()

        vm.updateName("高数")
        vm.addSession()
        advanceUntilIdle()
        vm.updateSessionDay(vm.keyAt(1), 3)
        vm.updateSessionStartMinutes(vm.keyAt(1), 10 * 60)
        vm.updateSessionEndMinutes(vm.keyAt(1), 10 * 60 + 45)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertEquals("整门课只提交一次", 1, repo.upsertCount)
        val saved = repo.lastUpserted
        assertNotNull(saved)
        assertEquals(2, saved!!.sessions.size)
        assertEquals(1, saved!!.sessions[0].dayOfWeek)
        assertEquals(3, saved!!.sessions[1].dayOfWeek)
        assertEquals(10 * 60, saved!!.sessions[1].startTime)
        assertEquals(10 * 60 + 45, saved!!.sessions[1].endTime)
        assertEquals("高数", saved!!.name)
        // 三列镜像 = 第一个课时
        assertEquals(1, saved!!.dayOfWeek)
        assertEquals(8 * 60, saved!!.startTime)
        assertTrue(vm.saved.value)
    }

    @Test
    fun `保存成功后重排课程提醒`() = runTest(testDispatcher) {
        val repo = newRepository()
        val rescheduler = FakeCourseReminderRescheduler()
        val vm = viewModel(repo, rescheduler)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.updateName("高数")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertEquals(1, rescheduler.callCount)
    }

    @Test
    fun `删课时后保存只提交剩下的课时`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.updateName("高数")
        vm.addSession()
        advanceUntilIdle()
        vm.removeSession(vm.keyAt(1))
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertEquals(1, repo.upsertCount)
        assertEquals(1, repo.lastUpserted!!.sessions.size)
    }

    // ===== 冲突定位 =====

    @Test
    fun `冲突回写到具体课时`() = runTest(testDispatcher) {
        val repo = newRepository()
        // 已有一门「线性代数」：周三 09:00-10:00
        repo.seedCourse(course(id = 10L, name = "线性代数", dayOfWeek = 3, startTime = 9 * 60, endTime = 10 * 60))
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()

        vm.updateName("高数")
        vm.addSession()
        advanceUntilIdle()
        // 第二个课时挪到周三 09:00-10:00 → 撞上「线性代数」
        vm.updateSessionDay(vm.keyAt(1), 3)
        vm.updateSessionStartMinutes(vm.keyAt(1), 9 * 60)
        vm.updateSessionEndMinutes(vm.keyAt(1), 10 * 60)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        assertEquals("冲突的课时要带上错误", "与《线性代数》时间冲突", sessions[1].errorMessage)
        assertNull("没冲突的课时不该有错误", sessions[0].errorMessage)
        assertNotNull("页面底部给一条汇总", vm.uiState.value.errorMessage)
        assertTrue("冲突时不能算保存成功", !vm.saved.value)
        assertEquals("冲突时不能写库", 0, repo.upsertCount)
    }

    @Test
    fun `同一门课内部课时重叠也回写到具体课时`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()

        vm.updateName("高数")
        vm.addSession()
        advanceUntilIdle()
        // 第二个课时跟第一个完全重合
        vm.updateSessionDay(vm.keyAt(1), 1)
        vm.updateSessionStartMinutes(vm.keyAt(1), 8 * 60)
        vm.updateSessionEndMinutes(vm.keyAt(1), 9 * 60 + 45)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        assertNotNull(sessions[0].errorMessage)
        assertNotNull(sessions[1].errorMessage)
        assertTrue(sessions[0].errorMessage!!.contains("重叠"))
        assertEquals(0, repo.upsertCount)
    }

    @Test
    fun `课程不存在时报错`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)

        vm.initialize(999L)
        advanceUntilIdle()

        assertEquals("课程不存在", vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.sessions.isEmpty())
    }

    // ===== 时间冲突 → 「仍然保存」 =====

    /** 造一个「撞上「线性代数」」的编辑态：第二个课时挪到周三 09:00-10:00 */
    private suspend fun kotlinx.coroutines.test.TestScope.conflictingViewModel(
        repo: FakeTimetableRepository,
    ): CourseEditViewModel {
        // 已有一门「线性代数」：周三 09:00-10:00
        repo.seedCourse(
            course(id = 10L, name = "线性代数", dayOfWeek = 3, startTime = 9 * 60, endTime = 10 * 60),
        )
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.updateName("高数")
        vm.addSession()
        advanceUntilIdle()
        // 第二个课时挪到周三 09:00-10:00 → 撞上「线性代数」
        vm.updateSessionDay(vm.keyAt(1), 3)
        vm.updateSessionStartMinutes(vm.keyAt(1), 9 * 60)
        vm.updateSessionEndMinutes(vm.keyAt(1), 10 * 60)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `冲突时不 force 保存失败并给出冲突信息`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = conflictingViewModel(repo)

        vm.save()
        advanceUntilIdle()

        // 没写库
        assertEquals("冲突时不能写库", 0, repo.upsertCount)
        assertTrue("冲突时不能算保存成功", !vm.saved.value)
        // 冲突信息要交给 UI 弹「仍然保存」框
        val conflict = vm.uiState.value.conflict
        assertNotNull("冲突时要给出冲突信息", conflict)
        assertEquals(listOf("线性代数"), conflict!!.conflictingCourseNames)
        assertEquals(0, conflict.internalOverlapCount)
        assertTrue(conflict.summary.contains("线性代数"))
    }

    @Test
    fun `force 保存时忽略冲突并写入`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = conflictingViewModel(repo)

        // 先撞一次（拿到冲突信息）
        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.conflict)
        assertEquals(0, repo.upsertCount)

        // 点「仍然保存」→ save(force = true)
        vm.save(force = true)
        advanceUntilIdle()

        assertEquals("force 时要写库", 1, repo.upsertCount)
        assertTrue("force 时算保存成功", vm.saved.value)
        assertNull("force 时对话框要收起", vm.uiState.value.conflict)
        assertEquals(2, repo.lastUpserted!!.sessions.size)
    }

    @Test
    fun `取消冲突对话框只收起对话框保留行内错误`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = conflictingViewModel(repo)

        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.conflict)

        vm.dismissConflict()
        advanceUntilIdle()

        assertNull("对话框要收起", vm.uiState.value.conflict)
        assertEquals(
            "行内错误要留着（那才是「哪一行撞了」的指引）",
            "与《线性代数》时间冲突",
            vm.uiState.value.sessions[1].errorMessage,
        )
        assertEquals("取消不等于保存", 0, repo.upsertCount)
    }

    @Test
    fun `改了排课之后旧的冲突信息作废`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = conflictingViewModel(repo)

        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.conflict)

        // 把第二个课时挪走 → 之前的「撞了线性代数」结论不再成立
        vm.updateSessionDay(vm.keyAt(1), 4)
        advanceUntilIdle()

        assertNull("排课变了就不要再拿旧结论问用户", vm.uiState.value.conflict)
    }

    @Test
    fun `普通校验错误不弹冲突对话框`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()

        // 名称为空 → isValid = false → save 直接返回，不写库也不给冲突
        vm.save()
        advanceUntilIdle()

        assertNull("普通校验错误不该有冲突信息", vm.uiState.value.conflict)
        assertEquals(0, repo.upsertCount)
    }

    @Test
    fun `课时时间非法只走行内错误不弹冲突对话框`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.updateName("高数")
        advanceUntilIdle()

        // 结束早于开始 → 行内错误
        vm.updateSessionEndMinutes(vm.keyAt(0), 8 * 60)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.sessions[0].errorMessage)
        assertNull("时间非法不是冲突，不该弹框", vm.uiState.value.conflict)
        assertTrue("时间非法时整门课不能保存", !vm.uiState.value.isValid)
    }

    @Test
    fun `本课程内部课时重叠也算冲突并能强制保存`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.updateName("高数")
        vm.addSession()
        advanceUntilIdle()
        // 第二个课时跟第一个完全重合
        vm.updateSessionDay(vm.keyAt(1), 1)
        vm.updateSessionStartMinutes(vm.keyAt(1), 8 * 60)
        vm.updateSessionEndMinutes(vm.keyAt(1), 9 * 60 + 45)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        val conflict = vm.uiState.value.conflict
        assertNotNull("内部重叠也要走冲突流程", conflict)
        assertEquals(0, conflict!!.conflictingCourseNames.size)
        assertEquals(1, conflict.internalOverlapCount)
        assertEquals(0, repo.upsertCount)

        vm.save(force = true)
        advanceUntilIdle()

        assertEquals("force 时要写库", 1, repo.upsertCount)
        assertTrue(vm.saved.value)
    }

    // ===== Phase 28 P9：按学期排提醒 + 按 key 定位课时 =====

    @Test
    fun `保存时按课程所属的学期重排提醒`() = runTest(testDispatcher) {
        val repo = newRepository()
        val rescheduler = FakeCourseReminderRescheduler()
        val vm = viewModel(repo, rescheduler)
        vm.initialize(0L)
        advanceUntilIdle()

        // 这门课属于「2023-2024-1」，不是当前学期
        vm.updateSemester("2023-2024-1")
        vm.updateName("高数")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertEquals(1, rescheduler.callCount)
        assertEquals(
            "提醒要按这门课所属的学期排，不是笼统的当前学期",
            "2023-2024-1",
            rescheduler.lastSemesterName,
        )
    }

    @Test
    fun `编辑别的学期的课时按那个学期重排提醒`() = runTest(testDispatcher) {
        val repo = newRepository()
        // 另一个学期（不是当前学期）
        repo.seedSemester(Semester(name = "2023-2024-1", startDate = 0L, endDate = 100L, isCurrent = false))
        repo.seedCourse(course(id = 10L, name = "线性代数", semesterName = "2023-2024-1"))
        val rescheduler = FakeCourseReminderRescheduler()
        val vm = viewModel(repo, rescheduler)

        vm.initialize(10L)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertEquals(1, rescheduler.callCount)
        assertEquals("2023-2024-1", rescheduler.lastSemesterName)
    }

    @Test
    fun `每个课时都有唯一的本地 key`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.addSession()
        vm.addSession()
        advanceUntilIdle()

        val keys = vm.uiState.value.sessions.map { it.localKey }
        assertEquals(3, keys.size)
        assertEquals("本地 key 必须唯一", keys.size, keys.toSet().size)
        assertTrue("本地 key 应该是负数（跟落库 id 区分开）", keys.all { it < 0L })
    }

    @Test
    fun `删掉一行后按 key 改另一行仍然改对那一行`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.addSession()
        vm.addSession()
        advanceUntilIdle()

        // 记下第三行的 key（删掉第一行后它会往前挪，但 key 不变）
        val thirdKey = vm.keyAt(2)
        val firstKey = vm.keyAt(0)

        // 删掉第一行
        vm.removeSession(firstKey)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.sessions.size)

        // 用 key 改第三行 —— 改的必须是「第三行」，不是现在的第二行
        vm.updateSessionDay(thirdKey, 5)
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        assertEquals("改的是还活着的那一行", 5, sessions[1].dayOfWeek)
        assertEquals("另一行不该被动", 1, sessions[0].dayOfWeek)
    }

    @Test
    fun `用不存在的 key 改课时不会误伤别的行`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.addSession()
        advanceUntilIdle()

        val before = vm.uiState.value.sessions.map { it.dayOfWeek }

        vm.updateSessionDay(localKey = -999L, dayOfWeek = 6)
        vm.removeSession(localKey = -999L)
        advanceUntilIdle()

        val after = vm.uiState.value.sessions
        assertEquals("不该删掉任何一行", before.size, after.size)
        assertEquals("不该改到任何一行", before, after.map { it.dayOfWeek })
    }

    // ===== Phase 28 P10：课时的教师 / 地点 =====

    @Test
    fun `添加课时继承前一个课时的教师与地点`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.addSession()
        advanceUntilIdle()

        // 给**最后一个**课时单独设教师 / 地点 ——
        // 再加一个课时时，新课时继承的是 sessions.last()，也就是它
        val lastKey = vm.keyAt(vm.uiState.value.sessions.lastIndex)
        vm.updateSessionTeacher(lastKey, "李老师")
        vm.updateSessionLocation(lastKey, "教三 201")
        advanceUntilIdle()

        // 再加一个课时 → 继承前一个的
        vm.addSession()
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        assertEquals(3, sessions.size)
        assertEquals("新课时继承前一个课时的教师", "李老师", sessions[2].teacher)
        assertEquals("新课时继承前一个课时的地点", "教三 201", sessions[2].location)
    }

    @Test
    fun `改课程级教师只填空值课时`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.addSession()
        advanceUntilIdle()

        // 第二个课时单独设了教师
        val secondKey = vm.keyAt(1)
        vm.updateSessionTeacher(secondKey, "王老师")
        advanceUntilIdle()

        // 改课程级教师
        vm.updateTeacher("李老师")
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        assertEquals("课程级教师改了", "李老师", vm.uiState.value.teacher)
        assertEquals("空值课时应被填上", "李老师", sessions[0].teacher)
        assertEquals("已单独设置过的课时保持不变", "王老师", sessions[1].teacher)
    }

    @Test
    fun `连续改课程级教师时沿用课程级的课时跟着变`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()

        // 第一次改课程级教师 → 空值课时被填上
        vm.updateTeacher("李老师")
        advanceUntilIdle()
        assertEquals("第一次改后空值课时应被填上", "李老师", vm.uiState.value.sessions[0].teacher)

        // 第二次改课程级教师 → 这些课时是「跟着课程级」的，必须继续跟着变。
        // 只判「值为空」的话，它们已经非空了，会被误判成「用户单独指定过」而从此失联。
        vm.updateTeacher("王老师")
        advanceUntilIdle()
        assertEquals("第二次改后应继续跟着课程级", "王老师", vm.uiState.value.sessions[0].teacher)
    }

    @Test
    fun `改课程级地点只填空值课时`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.addSession()
        advanceUntilIdle()

        val secondKey = vm.keyAt(1)
        vm.updateSessionLocation(secondKey, "操场")
        advanceUntilIdle()

        vm.updateLocation("教三 201")
        advanceUntilIdle()

        val sessions = vm.uiState.value.sessions
        assertEquals("空值课时应被填上", "教三 201", sessions[0].location)
        assertEquals("已单独设置过的课时保持不变", "操场", sessions[1].location)
    }

    @Test
    fun `课时的教师与地点可以单独设置`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()

        val key = vm.keyAt(0)
        vm.updateSessionTeacher(key, "李老师")
        vm.updateSessionLocation(key, "教三 201")
        advanceUntilIdle()

        val session = vm.uiState.value.sessions[0]
        assertEquals("李老师", session.teacher)
        assertEquals("教三 201", session.location)
    }

    @Test
    fun `保存时逐课时带上自己的教师与地点`() = runTest(testDispatcher) {
        val repo = newRepository()
        val vm = viewModel(repo)
        vm.initialize(0L)
        advanceUntilIdle()
        vm.addSession()
        advanceUntilIdle()

        val secondKey = vm.keyAt(1)
        vm.updateSessionTeacher(secondKey, "王老师")
        vm.updateSessionLocation(secondKey, "操场")
        vm.updateName("高数")
        vm.updateSemester(semester.name) // 新建课程时学期必填，缺了 save() 直接返回不落库
        advanceUntilIdle()

        // 两个课时的默认时段相同（第 1-2 节），会触发同门课内部重叠而被冲突拦下。
        // 这里 force 保存 —— 本例要验的是「逐课时带上自己的教师与地点」，
        // 正常保存路径由「保存时整门课一次性提交」那条用例守着。
        vm.save(force = true)
        advanceUntilIdle()

        val saved = repo.lastUpserted!!
        assertEquals(2, saved.sessions.size)
        assertEquals("第一个课时没单独设置 → 空串（沿用课程级）", "", saved.sessions[0].teacher)
        assertEquals("第二个课时带自己的教师", "王老师", saved.sessions[1].teacher)
        assertEquals("第二个课时带自己的地点", "操场", saved.sessions[1].location)
    }

    @Test
    fun `载入时值等于课程级的课时归一化成空串`() = runTest(testDispatcher) {
        val repo = newRepository()
        // 这门课的课时 teacher / location 跟课程级一样（= 迁移回填后的样子）
        repo.seedCourse(
            course(id = 10L, name = "高数", teacher = "李老师", location = "教三 201"),
        )
        repo.seedSessions(
            10L,
            listOf(
                CourseSession(
                    courseId = 10L,
                    dayOfWeek = 1,
                    startTime = 8 * 60,
                    endTime = 9 * 60 + 45,
                    teacher = "李老师",
                    location = "教三 201",
                ),
            ),
        )
        val vm = viewModel(repo)
        vm.initialize(10L)
        advanceUntilIdle()

        val session = vm.uiState.value.sessions[0]
        assertEquals("与课程级相同 → 当作没单独设置", "", session.teacher)
        assertEquals("与课程级相同 → 当作没单独设置", "", session.location)
        // 但生效值仍是课程级那份
        assertEquals("李老师", session.resolvedTeacher(vm.uiState.value.teacher))
        assertEquals("教三 201", session.resolvedLocation(vm.uiState.value.location))
    }

    @Test
    fun `载入时与课程级不同的课时值保留`() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.seedCourse(course(id = 10L, name = "高数", teacher = "李老师", location = "教三 201"))
        repo.seedSessions(
            10L,
            listOf(
                CourseSession(
                    courseId = 10L,
                    dayOfWeek = 1,
                    startTime = 8 * 60,
                    endTime = 9 * 60 + 45,
                    teacher = "外教 Tom",
                    location = "外教楼 301",
                ),
            ),
        )
        val vm = viewModel(repo)
        vm.initialize(10L)
        advanceUntilIdle()

        val session = vm.uiState.value.sessions[0]
        assertEquals("单独设置过的教师要保留", "外教 Tom", session.teacher)
        assertEquals("单独设置过的地点要保留", "外教楼 301", session.location)
    }
}
