package com.example.personalassistant.feature.timetable.presentation.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.ui.theme.CoursePalette
import com.example.personalassistant.feature.timetable.domain.model.CourseConflict
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import com.example.personalassistant.feature.timetable.domain.usecase.CourseConflictException
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveTimeSlotScheduleUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.UpsertCourseUseCase
import com.example.personalassistant.service.reminder.CourseReminderRescheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 课程编辑页 ViewModel（Phase 28 重写：一门课 = 课程本体 + N 个课时）
 *
 * ## 与旧版的差别
 *
 * 旧版只有**一组**时间控件（全局 星期 / 开始 / 结束 / 第 N 节），
 * 一门课只能有一个上课时段。改课表时「周一 1-2 节、周三 3-4 节」只能记成其中一条。
 *
 * 现在时间下沉到 [CourseEditUiState.sessions]：
 * - 每个课时有自己的星期几、起止时间、时间模式、展开态、错误
 * - 每课时可独立增 / 删 / 改
 * - 保存时**整门课一次性提交**（`upsertCourseWithSessions` 一个事务）
 *
 * ## 保存语义
 *
 * 一门课是一个语义单元：用户可能改了第 3 个课时的周三时间，也可能顺手删了第 2 个。
 * 「保存」= 把当前列表里的 N 个课时整体写回，而不是「每次改动立刻落库」。
 * 立即落库会留下半成品（加了课时没填时间 → 库里多一条脏数据）。
 */
@HiltViewModel
class CourseEditViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val upsertCourseUseCase: UpsertCourseUseCase,
    private val observeTimeSlotSchedule: ObserveTimeSlotScheduleUseCase,
    private val reminderRescheduler: CourseReminderRescheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseEditUiState())
    val uiState: StateFlow<CourseEditUiState> = _uiState.asStateFlow()

    /** 完成回调：true 表示保存成功，UI 层据此导航返回 */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** 删除完成回调 */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        // 持续观察 TimeSlotSchedule（用户在设置页改了课时表，编辑页的下拉也得刷新）
        viewModelScope.launch {
            observeTimeSlotSchedule().collect { schedule ->
                _uiState.update { it.copy(availableTimeSlots = schedule) }
            }
        }
    }

    /**
     * 载入编辑页
     *
     * - `courseId <= 0`：新建 —— 预置**一个**课时（周一 / 第 1 节），并展开
     * - `courseId > 0`：编辑 —— 读 `getCourseWithSessions`，
     *   把 `effectiveSessions` 摊成课时列表（老数据没有课时记录时靠主时段镜像兜底）
     */
    fun initialize(courseId: Long) {
        viewModelScope.launch {
            val semesters = repository.observeAllSemesters().first().map { it.name }
            val currentSem = repository.observeCurrentSemester().first()
            val timeSlots = observeTimeSlotSchedule().first()

            if (courseId <= 0L) {
                // 新建：默认用当前学期 + 第一个颜色 + 一个默认课时（周一 / 第 1 节）
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEdit = false,
                        availableSemesters = semesters,
                        semester = currentSem?.name ?: semesters.firstOrNull().orEmpty(),
                        availableTimeSlots = timeSlots,
                        sessions = listOf(defaultSession(timeSlots, expanded = true)),
                        colorIndex = 0,
                    )
                }
            } else {
                val course = repository.getCourseWithSessions(courseId)
                if (course == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "课程不存在") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEdit = true,
                        availableSemesters = semesters,
                        availableTimeSlots = timeSlots,
                        id = course.id,
                        name = course.name,
                        teacher = course.teacher,
                        location = course.location,
                        sessions = course.effectiveSessions.map { session ->
                            sessionStateOf(session, timeSlots)
                        },
                        semester = course.semester ?: currentSem?.name.orEmpty(),
                        weeks = course.weeks.orEmpty(),
                        reminderMinutes = course.reminderMinutes,
                        note = course.note.orEmpty(),
                        creditText = course.credit?.toString().orEmpty(),
                        gradePointText = course.gradePoint?.toString().orEmpty(),
                        colorIndex = CoursePalette.indexOfFirst { c ->
                            c.value.toLong() == course.color.toLong()
                        }.takeIf { idx -> idx >= 0 } ?: 0,
                    )
                }
            }
        }
    }

    // ===== 课程本体字段 =====

    fun updateName(v: String) = _uiState.update {
        it.copy(name = v, nameError = null, errorMessage = null)
    }

    fun updateTeacher(v: String) = _uiState.update { it.copy(teacher = v) }
    fun updateLocation(v: String) = _uiState.update { it.copy(location = v) }
    fun updateColorIndex(v: Int) = _uiState.update { it.copy(colorIndex = v) }
    fun updateSemester(v: String) = _uiState.update { it.copy(semester = v) }
    fun updateWeeks(v: String) = _uiState.update { it.copy(weeks = v) }
    fun updateReminderMinutes(v: Int) = _uiState.update { it.copy(reminderMinutes = v) }
    fun updateNote(v: String) = _uiState.update { it.copy(note = v) }
    fun updateCreditText(v: String) = _uiState.update { it.copy(creditText = v) }
    fun updateGradePointText(v: String) = _uiState.update { it.copy(gradePointText = v) }

    // ===== 课时列表 =====

    /**
     * 本地 key 发放器（Phase 28 P9）
     *
     * 负数自增 id（-1, -2, -3…），编辑页生命周期内唯一。
     * 用负数是为了跟**落库的课时 id**（正数）区分开 —— 万一哪天有人拿它去查库，
     * 查不到东西而不是误命中某条记录。
     */
    private var localKeySeq: Long = 0L

    private fun nextLocalKey(): Long = --localKeySeq

    /**
     * 追加一个默认课时（周一 / 第 1 节），并**自动展开**
     *
     * 展开是为了让用户一进来就看到「刚加的这条长什么样」，不用再点一下。
     */
    fun addSession() {
        _uiState.update { state ->
            val session = defaultSession(state.availableTimeSlots, expanded = true)
            state.copy(
                sessions = state.sessions + session,
                errorMessage = null,
                // 排课变了 → 上一次冲突的结论作废
                conflict = null,
            )
        }
    }

    /**
     * 删除 **localKey** 指定的课时
     *
     * ⚠️ **至少要留一个课时** —— 删光了就等于「把这门课删了」，
     * 而删除有独立的入口（顶栏的 🗑），这里静默删光会让用户误操作。
     *
     * 按 key 定位（不是下标）—— 下标会随删除而错位，key 不会。
     */
    fun removeSession(localKey: Long) {
        _uiState.update { state ->
            if (state.sessions.size <= 1) return@update state
            val target = state.sessions.indexOfFirst { it.localKey == localKey }
            if (target < 0) return@update state
            state.copy(
                sessions = state.sessions.toMutableList().also { it.removeAt(target) },
                errorMessage = null,
                // 排课变了 → 上一次冲突的结论作废
                conflict = null,
            )
        }
    }

    /** 展开 / 折叠 **localKey** 指定的课时 */
    fun toggleSessionExpanded(localKey: Long) = mutateSession(localKey) { session, _ ->
        session.copy(expanded = !session.expanded)
    }

    /** 通用改法：直接替换 **localKey** 指定课时的编辑态 */
    fun updateSession(localKey: Long, transform: (SessionEditState) -> SessionEditState) =
        mutateSession(localKey) { session, _ -> transform(session) }

    /** 改星期几 */
    fun updateSessionDay(localKey: Long, dayOfWeek: Int) = mutateSession(localKey) { session, _ ->
        session.copy(dayOfWeek = dayOfWeek.coerceIn(1, 7))
    }

    /**
     * 改时间模式（时段 / 选第 N 节）
     *
     * 切到 PERIOD 时按当前分钟数反推一下「第几节」，让 chip 尽量对得上；
     * 切到 CLOCK 时分钟数原样保留（用户填的 HH:mm 不该被改）。
     */
    fun updateSessionMode(localKey: Long, mode: TimeMode) = mutateSession(localKey) { session, schedule ->
        if (mode == session.mode) return@mutateSession session
        if (mode == TimeMode.CLOCK) {
            session.copy(mode = TimeMode.CLOCK)
        } else {
            val startIndex = schedule.periods.firstOrNull { it.startMinutes == session.startMinutes }?.index
                ?: schedule.periods.firstOrNull()?.index
                ?: session.startPeriodIndex
            val endIndex = schedule.periods.firstOrNull { it.endMinutes == session.endMinutes }?.index
                ?: schedule.periods.getOrNull(schedule.periods.indexOfFirst { it.index == startIndex } + 1)?.index
                ?: startIndex
            session.copy(
                mode = TimeMode.PERIOD,
                startPeriodIndex = startIndex,
                endPeriodIndex = endIndex,
            )
        }
    }

    /** 改开始时间（时段模式，分钟数） */
    fun updateSessionStartMinutes(localKey: Long, minutes: Int) = mutateSession(localKey) { session, _ ->
        session.copy(
            startMinutes = minutes.coerceIn(0, 1439),
            errorMessage = validateTimes(minutes.coerceIn(0, 1439), session.endMinutes),
        )
    }

    /** 改结束时间（时段模式，分钟数） */
    fun updateSessionEndMinutes(localKey: Long, minutes: Int) = mutateSession(localKey) { session, _ ->
        val end = minutes.coerceIn(0, 1440)
        session.copy(
            endMinutes = end,
            errorMessage = validateTimes(session.startMinutes, end),
        )
    }

    /**
     * 改开始节（选第 N 节）
     *
     * 按课时表把「第 N 节」翻成分钟**并写回** —— 分钟数是权威值（见 [SessionEditState]）。
     */
    fun updateSessionStartPeriod(localKey: Long, periodIndex: Int) =
        mutateSession(localKey) { session, schedule ->
            val period = schedule.periods.firstOrNull { it.index == periodIndex }
                ?: return@mutateSession session
            session.copy(
                mode = TimeMode.PERIOD,
                startPeriodIndex = periodIndex,
                startMinutes = period.startMinutes,
                errorMessage = validateTimes(period.startMinutes, session.endMinutes),
            )
        }

    /** 改结束节（选第 N 节） */
    fun updateSessionEndPeriod(localKey: Long, periodIndex: Int) =
        mutateSession(localKey) { session, schedule ->
            val period = schedule.periods.firstOrNull { it.index == periodIndex }
                ?: return@mutateSession session
            session.copy(
                mode = TimeMode.PERIOD,
                endPeriodIndex = periodIndex,
                endMinutes = period.endMinutes,
                errorMessage = validateTimes(session.startMinutes, period.endMinutes),
            )
        }

    // ===== 保存 / 删除 =====

    /**
     * 保存整门课（一次性提交）
     *
     * - 走 [UpsertCourseUseCase] → `upsertCourseWithSessions`（一个事务：课程 + 全部课时）
     * - 冲突时把错误**回写到具体课时**的 `errorMessage`（就近显示在行内），
     *   同时把冲突摘要放进 [CourseEditUiState.conflict] —— UI 据此弹「仍然保存」确认框
     * - 存成功后重排课程提醒闹钟（此前 `rescheduleAll` 全项目没有调用方，提醒从没排上）
     *   —— **按这门课所属的学期**排（Phase 28 P9），不是笼统的「当前学期」
     *
     * @param force true = 忽略冲突硬写（用户点了「仍然保存」）
     */
    fun save(force: Boolean = false) {
        val state = _uiState.value
        if (!state.isValid || state.isSaving) return
        _uiState.update {
            it.copy(
                isSaving = true,
                errorMessage = null,
                // 清掉上一次的冲突 → 「仍然保存」时对话框自动收起
                conflict = null,
                sessions = it.sessions.map { s -> s.copy(errorMessage = null) },
            )
        }
        viewModelScope.launch {
            try {
                val color = CoursePalette[state.colorIndex.coerceIn(0, CoursePalette.lastIndex)].value.toInt()
                upsertCourseUseCase(state.toCourse(color), force = force)
                // 课程变了 → 提醒闹钟跟着重排（失败不能影响保存结果）
                // Phase 28 P9：按**这门课所属的学期**排，不是「当前学期」
                runCatching { reminderRescheduler.rescheduleAll(state.semester.ifBlank { null }) }
                _saved.value = true
            } catch (e: CourseConflictException) {
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        errorMessage = conflictSummary(e.conflict),
                        conflict = e.conflict.toTimeConflict(),
                        sessions = markConflictingSessions(current.sessions, e.conflict),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    /**
     * 关掉「仍然保存」确认框（用户点「取消」）
     *
     * ⚠️ 只清对话框 —— 课时的行内错误**保留**：那才是「哪一行撞了」的指引，
     * 顺手删掉的话用户就不知道该去改哪儿了。
     */
    fun dismissConflict() = _uiState.update { it.copy(conflict = null) }

    /** 删除整门课（连带它的全部课时） */
    fun delete() {
        val state = _uiState.value
        if (!state.isEdit || state.id <= 0L) return
        viewModelScope.launch {
            runCatching { repository.deleteCourse(state.id) }
                .onSuccess {
                    // 课没了 → 旧闹钟也要撤
                    // Phase 28 P9：按**这门课所属的学期**撤，不是「当前学期」
                    runCatching { reminderRescheduler.rescheduleAll(state.semester.ifBlank { null }) }
                    _deleted.value = true
                }
                .onFailure { _uiState.update { s -> s.copy(errorMessage = it.message) } }
        }
    }

    // ===== 内部 =====

    /**
     * 改 **localKey** 指定的课时（找不到时什么都不做）
     *
     * 顺带把 [CourseEditUiState.conflict] 清掉 —— 排课一旦变了，
     * 上一次「撞了谁」的结论就不再成立，对话框不该继续拿旧结论问用户。
     */
    private fun mutateSession(
        localKey: Long,
        transform: (SessionEditState, TimeSlotSchedule) -> SessionEditState,
    ) {
        _uiState.update { state ->
            val target = state.sessions.indexOfFirst { it.localKey == localKey }
            if (target < 0) return@update state
            val current = state.sessions[target]
            val updated = transform(current, state.availableTimeSlots)
            if (updated == current) return@update state
            val list = state.sessions.toMutableList()
            list[target] = updated
            state.copy(sessions = list, conflict = null)
        }
    }

    /** 新建课程 / 「＋ 添加课时」用的默认课时：周一 / 第 1 节 */
    private fun defaultSession(schedule: TimeSlotSchedule, expanded: Boolean): SessionEditState {
        val first = schedule.periods.firstOrNull()
        val second = schedule.periods.getOrNull(1) ?: first
        return SessionEditState(
            localKey = nextLocalKey(),
            id = 0L,
            dayOfWeek = 1,
            mode = TimeMode.PERIOD,
            startMinutes = first?.startMinutes ?: 8 * 60,
            endMinutes = second?.endMinutes ?: 9 * 60 + 45,
            startPeriodIndex = first?.index ?: 1,
            endPeriodIndex = second?.index ?: 2,
            expanded = expanded,
        )
    }

    /**
     * 已落库的课时 → 编辑态
     *
     * ## mode 反推（Phase 28 放宽）
     *
     * 旧口径：开始**和**结束都要命中某个 period 才算 PERIOD 模式。
     * 多课时后命中率更低（每个课时的起止各不相同），于是大部分课时都掉回 CLOCK。
     *
     * 现在放宽为：**开始时刻命中任一 period 的 startMinutes 就用 PERIOD**。
     * 结束节取「结束时刻命中的那一节」，取不到就退到开始节的下一节。
     *
     * 分钟数始终按库里的原值保留（[SessionEditState] 的注释解释了为什么）。
     */
    private fun sessionStateOf(session: CourseSession, schedule: TimeSlotSchedule): SessionEditState {
        val startPeriod = schedule.periods.firstOrNull { it.startMinutes == session.startTime }
        val endPeriod = schedule.periods.firstOrNull { it.endMinutes == session.endTime }
        val lastIndex = schedule.periods.lastOrNull()?.index ?: 1
        return SessionEditState(
            localKey = nextLocalKey(),
            id = session.id,
            dayOfWeek = session.dayOfWeek,
            mode = if (startPeriod != null) TimeMode.PERIOD else TimeMode.CLOCK,
            startMinutes = session.startTime,
            endMinutes = session.endTime,
            startPeriodIndex = startPeriod?.index ?: 1,
            endPeriodIndex = (endPeriod?.index ?: (startPeriod?.index ?: 1) + 1).coerceAtMost(lastIndex),
            expanded = false,
        )
    }

    /** 时间的本地校验（就近显示在行内） */
    private fun validateTimes(start: Int, end: Int): String? = when {
        end > 1439 -> "结束时间必须在 24:00 之前"
        end <= start -> "结束时间必须晚于开始时间"
        else -> null
    }

    /** 冲突汇总（页面底部那条） */
    private fun conflictSummary(conflict: CourseConflict): String = buildString {
        append("时间与已有课程冲突（${conflict.conflicts.size} 门）")
        if (conflict.internalOverlaps.isNotEmpty()) {
            append("，本课程内部有 ${conflict.internalOverlaps.size} 处课时重叠")
        }
    }

    /**
     * 领域层冲突 → UI 层的冲突摘要（供「仍然保存」对话框用）
     *
     * 课程名去重 —— 两门课同名时列一次就够，用户看的是「撞了谁」不是「撞了几门」。
     */
    private fun CourseConflict.toTimeConflict(): CourseTimeConflict = CourseTimeConflict(
        conflictingCourseNames = conflicts.map { it.name }.distinct(),
        internalOverlapCount = internalOverlaps.size,
    )

    /**
     * 把冲突**定位到具体课时**
     *
     * 冲突明细里的课时是「按库里那份 Course 拼出来的」，
     * 星期 + 起止分钟跟编辑态里的一一对应，用它当 key 就能把错误写回那一行。
     */
    private fun markConflictingSessions(
        sessions: List<SessionEditState>,
        conflict: CourseConflict,
    ): List<SessionEditState> {
        val messages = mutableMapOf<String, MutableList<String>>()
        conflict.sessionDetails.forEach { detail ->
            messages.getOrPut(sessionKey(detail.session)) { mutableListOf() }
                .add("与《${detail.conflictingCourse.name}》时间冲突")
        }
        conflict.internalOverlaps.forEach { overlap ->
            messages.getOrPut(sessionKey(overlap.first)) { mutableListOf() }
                .add("与本课程另一个课时重叠")
            messages.getOrPut(sessionKey(overlap.second)) { mutableListOf() }
                .add("与本课程另一个课时重叠")
        }
        if (messages.isEmpty()) return sessions
        return sessions.map { session ->
            val msg = messages[sessionKeyOf(session)]?.distinct()?.joinToString("；")
            if (msg == null) session else session.copy(errorMessage = msg, expanded = true)
        }
    }

    private fun sessionKey(session: CourseSession): String =
        "${session.dayOfWeek}-${session.startTime}-${session.endTime}"

    private fun sessionKeyOf(session: SessionEditState): String =
        "${session.dayOfWeek}-${session.startMinutes}-${session.endMinutes}"
}
