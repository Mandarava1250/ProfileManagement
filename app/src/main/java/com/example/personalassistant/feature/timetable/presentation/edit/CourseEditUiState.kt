package com.example.personalassistant.feature.timetable.presentation.edit

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule
import com.example.personalassistant.feature.timetable.domain.model.resolveSessionMeta

/**
 * 课程编辑页 UI 状态
 *
 * ## Phase 28：一门课 = 课程本体 + N 个课时
 *
 * 改造前：编辑页只有**一组**时间控件（全局的 星期 / 开始 / 结束 / 第 N 节），
 * 只能表达「一门课 = 一个上课时段」。
 *
 * 改造后：时间下沉到 [sessions] —— 一门课有 N 个 [SessionEditState]，
 * 每个课时有自己的星期几、起止时间、时间模式、展开态和错误。
 *
 * ## 错误就近显示（不是一个全局 errorMessage 兜底）
 *
 * | 错误 | 落在哪 |
 * |---|---|
 * | 课程名称为空 | [nameError]（名称输入框下方） |
 * | 某个课时的冲突 / 时间非法 | [SessionEditState.errorMessage]（该课时行内） |
 * | 保存失败等页面级错误 | [errorMessage] |
 *
 * ## 时间冲突 ≠ 普通校验错误
 *
 * 普通校验错误（名称为空、课时时间非法）**就地显示**，不弹框。
 * 时间冲突（撞了别的课 / 本课程内部课时重叠）多一个选择：
 * 用户可以选择「仍然保存」硬写进去 —— 所以它单独走 [conflict] 这个字段，
 * 而不是塞进 [errorMessage] 里靠字符串去认。
 */
data class CourseEditUiState(
    val isLoading: Boolean = false,
    val isEdit: Boolean = false,
    val isSaving: Boolean = false,
    /** 页面级错误（保存失败、课程不存在等） */
    val errorMessage: String? = null,
    /** 课程名称下方的行内错误 */
    val nameError: String? = null,
    /**
     * 保存时检测到的**时间冲突**；非 null 时 UI 弹「仍然保存」确认框
     *
     * null = 没有冲突（或者用户已经关掉了对话框）。
     */
    val conflict: CourseTimeConflict? = null,

    // ===== 课程本体 =====
    val id: Long = 0L,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",

    /**
     * 这门课的**全部课时**（Phase 28 新增）
     *
     * 空列表只在「还没初始化」时出现 —— 正常流程下至少有一个课时
     * （新建时预置一条，编辑时来自 `Course.effectiveSessions`）。
     */
    val sessions: List<SessionEditState> = emptyList(),

    /** 当前课时表（用户在设置页改了，这里也会跟着刷新） */
    val availableTimeSlots: TimeSlotSchedule = TimeSlotSchedule.default(),

    val colorIndex: Int = 0,
    val semester: String = "",
    val weeks: String = "",            // 空表示全学期
    val reminderMinutes: Int = 15,
    val note: String = "",

    /** 学分（Phase 12 新增；空字符串 = 未填，落库为 null） */
    val creditText: String = "",

    /** 绩点 0.0-4.0（Phase 12 新增；空字符串 = 未填，落库为 null） */
    val gradePointText: String = "",

    val availableSemesters: List<String> = emptyList(),
) {
    /**
     * 这门课现在能不能保存
     *
     * = 名称非空 && 至少一个课时 && 每个课时都合法
     *
     * 学期规则沿用旧口径：**新建**时必须选一个学期（编辑时允许留空）。
     */
    val isValid: Boolean
        get() {
            if (name.isBlank()) return false
            if (!isEdit && semester.isBlank()) return false
            if (sessions.isEmpty()) return false
            return sessions.all { it.isValid }
        }

    /**
     * 拼出领域模型（整门课一次提交）
     *
     * - [Course.sessions] = 全部课时（按列表顺序 = sortOrder）
     * - `courses` 表的三列镜像 = **第一个课时**（老数据 / 旧备份没有课时记录时靠它兜底）
     *
     * ## 课时级的教师 / 地点
     *
     * 逐课时带上**自己的**值（空串就写空串）。
     * 「空串 = 沿用课程级」这条回落规则在**领域层**统一实现
     * （[com.example.personalassistant.feature.timetable.domain.model.resolvedTeacher]），
     * 这里不烤进课程级的值 —— 烤进去的话，用户后来改课程级教师，
     * 这些课时就永远跟着旧值走了。
     *
     * ⚠️ 调用前请先看 [isValid] —— 非法时间（结束早于开始等）会让
     * [CourseSession] 的 `require` 直接抛异常。
     */
    fun toCourse(defaultColor: Int): Course {
        val domainSessions = sessions.mapIndexed { index, session ->
            CourseSession(
                id = 0L,
                courseId = 0L,
                dayOfWeek = session.dayOfWeek,
                startTime = session.startMinutes,
                endTime = session.endMinutes,
                sortOrder = index,
                teacher = session.teacher.trim(),
                location = session.location.trim(),
            )
        }
        val first = domainSessions.firstOrNull()
        return Course(
            id = id,
            name = name.trim(),
            teacher = teacher.trim(),
            location = location.trim(),
            dayOfWeek = first?.dayOfWeek ?: 1,
            startTime = first?.startTime ?: 8 * 60,
            endTime = first?.endTime ?: 9 * 60 + 45,
            color = defaultColor,
            semester = semester.ifBlank { null },
            weeks = weeks.trim().ifBlank { null },
            reminderMinutes = reminderMinutes,
            note = note.trim().ifBlank { null },
            // 空串 → null，非法输入（非数字）→ null（不让用户因手滑写坏 DB）
            credit = creditText.trim().toDoubleOrNull()?.takeIf { it > 0.0 },
            gradePoint = gradePointText.trim().toDoubleOrNull()?.takeIf { it in 0.0..4.0 },
            sessions = domainSessions,
        )
    }
}

/**
 * 保存时检测到的**时间冲突**
 *
 * 与「普通校验错误」的区别：普通校验错误（名称为空、时间非法）**就地显示**在输入处，
 * 改对了就行；时间冲突是「数据本身合法，但和别的课撞了」——
 * 用户有时确实想硬写进去（比如临时调课、老师改点了），所以给它一个「仍然保存」的出口。
 *
 * @param conflictingCourseNames 撞上的**已有课程名**（去重，按检测顺序）
 * @param internalOverlapCount 本课程**内部**课时重叠的对数
 */
data class CourseTimeConflict(
    val conflictingCourseNames: List<String> = emptyList(),
    val internalOverlapCount: Int = 0,
) {
    /**
     * 冲突摘要（一行话）
     *
     * 例：`与已有课程《线性代数》《大学物理》时间冲突；本课程内部有 1 处课时重叠`
     */
    val summary: String
        get() = buildString {
            if (conflictingCourseNames.isNotEmpty()) {
                append("与已有课程")
                append(conflictingCourseNames.joinToString("、") { "《$it》" })
                append("时间冲突")
            }
            if (internalOverlapCount > 0) {
                if (isNotEmpty()) append("；")
                append("本课程内部有 $internalOverlapCount 处课时重叠")
            }
        }
}

/**
 * 一个课时的编辑态（Phase 28 新增）
 *
 * 对应领域模型 [CourseSession]，但多了两样 UI 才需要的东西：
 * - [expanded]：这一行是不是展开着（折叠态只显示一行摘要）
 * - [errorMessage]：这一行自己的错误（冲突 / 时间非法），就近显示在行内
 *
 * ## 时间模式
 *
 * 每个课时**单独**记自己的模式（[mode]）：
 * - CLOCK → 直接填 HH:mm
 * - PERIOD → 从 [TimeSlotSchedule] 里选「第 N 节」
 *
 * ## ⚠️ 分钟数是权威值
 *
 * PERIOD 模式下，[startMinutes] / [endMinutes] 依然是**落库用的真实时间**：
 * 用户每次改「开始节 / 结束节」时，ViewModel 会按课时表把 index 翻成分钟写回这里。
 *
 * 为什么不改成「保存时再按 index 翻」？
 * 因为反推模式放宽成「**开始时刻命中任一节的开始**就算 PERIOD」之后，
 * 会出现「08:00-10:00」这种**结束时刻不在节表上**的课时 ——
 * 保存时按 index 翻会把结束时间悄悄改成所在那一节的结束（10:00 → 10:40）。
 * 保留分钟数为权威值就杜绝了这种静默改数据。
 */
data class SessionEditState(
    /**
     * Phase 28 P9：**本地唯一 key**（编辑页内定位这一行用）
     *
     * ## 为什么不用列表下标
     *
     * 下标是**位置**，不是**身份**：
     * - 删掉第 1 行后，原本第 2 行的下标从 1 变成 0
     * - 如果 UI 层在同一次 composition 里先删后改（或者重组时拿到的是旧快照），
     *   按下标就会改错行
     *
     * Compose 每次重组都是新快照，实践中不会错位 —— 但那是**约定**，不是**保证**。
     * 给每个课时发一个本地唯一 key，增删改一律按 key 定位，错位就不可能发生。
     *
     * ## 取值
     *
     * ViewModel 发的**负数自增 id**（-1, -2, -3…），编辑页生命周期内唯一。
     * 不落库 —— 保存时 [CourseEditUiState.toCourse] 只取业务字段。
     */
    val localKey: Long = 0L,
    /** 课时 id；0 = 还没落库的新课时 */
    val id: Long = 0L,
    /** 星期几，1=周一 … 7=周日 */
    val dayOfWeek: Int = 1,
    val mode: TimeMode = TimeMode.CLOCK,
    /** 开始时间（当天分钟数，0-1439） */
    val startMinutes: Int = 8 * 60,
    /** 结束时间（当天分钟数，0-1439，且 > startMinutes） */
    val endMinutes: Int = 9 * 60 + 45,
    /** PERIOD 模式：开始是第几节 */
    val startPeriodIndex: Int = 1,
    /** PERIOD 模式：结束是第几节 */
    val endPeriodIndex: Int = 2,
    /** 这一行是不是展开着 */
    val expanded: Boolean = false,
    /** 这一行自己的错误（冲突 / 时间非法） */
    val errorMessage: String? = null,
    /**
     * Phase 28 P10：这个课时**自己**的授课教师
     *
     * 空串 = 沿用课程级的（见 [CourseEditUiState.teacher]）。
     * 输入框留空就是「我不单独指定」—— 折叠摘要里显示的仍是课程级那份。
     */
    val teacher: String = "",
    /**
     * Phase 28 P10：这个课时**自己**的上课地点
     *
     * 空串 = 沿用课程级的（见 [CourseEditUiState.location]）。
     */
    val location: String = "",
) {
    /** 落库用的分钟区间（见类注释：分钟数是权威值） */
    val effectiveMinutes: Pair<Int, Int> get() = startMinutes to endMinutes

    /** 这个课时本身合法吗（[CourseSession] 的 require 条件） */
    val isValid: Boolean
        get() = dayOfWeek in 1..7 &&
            startMinutes in 0..1439 &&
            endMinutes in 0..1439 &&
            endMinutes > startMinutes
}

/**
 * 课程编辑页的时间模式
 *
 * 每个课时单独一个（多课时后一门课里可能既有按时段的、也有按节的）。
 */
enum class TimeMode { CLOCK, PERIOD }

/**
 * 这个课时的**授课教师**（空 → 回落课程级）
 *
 * 口径与领域层共用 [resolveSessionMeta] —— 编辑页的折叠摘要
 * 跟课表上显示的必须是同一个值。
 *
 * @param courseTeacher 课程级的教师
 */
fun SessionEditState.resolvedTeacher(courseTeacher: String): String =
    resolveSessionMeta(teacher, courseTeacher)

/**
 * 这个课时的**上课地点**（空 → 回落课程级）
 *
 * 口径同 [resolvedTeacher]。
 *
 * @param courseLocation 课程级的地点
 */
fun SessionEditState.resolvedLocation(courseLocation: String): String =
    resolveSessionMeta(location, courseLocation)

/**
 * 课时表里「第 index 节」的起止分钟
 *
 * 找不到这一节时返回 null（调用方自己决定兜底成什么）。
 */
fun TimeSlotSchedule.periodMinutes(index: Int): Pair<Int, Int>? =
    periods.firstOrNull { it.index == index }?.let { it.startMinutes to it.endMinutes }
