package com.example.personalassistant.feature.timetable.data.repository

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 单元测试用的最小 [TimetableRepository] stub
 *
 * 只实现 [searchCourses] + 必要的 observe 兜底。其他方法全部返回默认值，
 * 因为 Sub-step 3 测试只需要 search 路径。
 *
 * ## Phase 28：课时
 *
 * 额外维护一份 `courseId → 课时列表` 的内存表，让「课程 + 课时」这条路径
 * 在测试里也能被真实验证（不只测纯函数）。
 *
 * 兜底语义与生产实现一致：**课程没有课时记录时，用主时段镜像兜底出一个单课时**。
 */
class FakeTimetableRepository : TimetableRepository {

    private val coursesState = MutableStateFlow<List<Course>>(emptyList())

    /** courseId → 课时列表（按 sortOrder 排序） */
    private val sessionsState = MutableStateFlow<Map<Long, List<CourseSession>>>(emptyMap())

    /** 学期列表（Phase 28 P7：编辑页新建课程时要选学期） */
    private val semestersState = MutableStateFlow<List<Semester>>(emptyList())

    var upsertCount: Int = 0
        private set

    /** 最近一次写入的课程（测试断言「整门课一次性提交」用） */
    var lastUpserted: Course? = null
        private set

    // ===== Course =====

    override fun observeCoursesBySemester(semester: String): Flow<List<Course>> =
        coursesState.map { list -> list.filter { it.semester == semester } }

    /**
     * Phase 12：观察全部学期所有课程
     */
    override fun observeAllCourses(): Flow<List<Course>> = coursesState

    override fun observeCoursesByDay(semester: String, dayOfWeek: Int): Flow<List<Course>> =
        coursesState.map { list -> list.filter { it.semester == semester && it.dayOfWeek == dayOfWeek } }

    override suspend fun getCourseById(id: Long): Course? =
        coursesState.value.firstOrNull { it.id == id }

    override suspend fun findConflicts(
        semester: String?,
        dayOfWeek: Int,
        startTime: Int,
        endTime: Int,
        excludeId: Long,
    ): List<Course> = coursesState.value.filter { c ->
        c.id != excludeId &&
            (semester == null || c.semester == semester) &&
            c.dayOfWeek == dayOfWeek &&
            c.startTime < endTime &&
            c.endTime > startTime
    }

    override suspend fun upsertCourse(course: Course): Long {
        upsertCount++
        val resolved = if (course.id > 0L) course else course.copy(id = (coursesState.value.size + 1).toLong())
        coursesState.value = coursesState.value + resolved
        return resolved.id
    }

    override suspend fun deleteCourse(id: Long) {
        coursesState.value = coursesState.value.filter { it.id != id }
        sessionsState.value = sessionsState.value - id
    }

    override suspend fun switchSemester(name: String) = Unit

    override suspend fun renameSemesterReferences(oldName: String, newName: String) {
        coursesState.value = coursesState.value.map {
            if (it.semester == oldName) it.copy(semester = newName) else it
        }
    }

    // ===== Phase 28：一门课多课时 =====

    override fun observeCoursesWithSessions(semester: String?): Flow<List<Course>> =
        coursesState.map { list ->
            list
                .filter { semester == null || it.semester == semester }
                .map { it.withSessionsFrom(sessionsState.value[it.id].orEmpty()) }
        }

    override suspend fun getCourseWithSessions(courseId: Long): Course? =
        coursesState.value.firstOrNull { it.id == courseId }
            ?.withSessionsFrom(sessionsState.value[courseId].orEmpty())

    override suspend fun upsertCourseWithSessions(course: Course): Long {
        upsertCount++
        lastUpserted = course
        val resolved = if (course.id > 0L) course else course.copy(id = (coursesState.value.size + 1).toLong())
        val sessions = course.sessions.sortedBy { it.sortOrder }
        val first = sessions.firstOrNull()
        val mirrored = resolved.copy(
            dayOfWeek = first?.dayOfWeek ?: resolved.dayOfWeek,
            startTime = first?.startTime ?: resolved.startTime,
            endTime = first?.endTime ?: resolved.endTime,
        )
        coursesState.value = coursesState.value.filter { it.id != mirrored.id } + mirrored
        sessionsState.value = sessionsState.value + (
            mirrored.id to sessions.mapIndexed { index, s ->
                s.copy(id = 0L, courseId = mirrored.id, sortOrder = index)
            }
            )
        return mirrored.id
    }

    override suspend fun findSessionConflicts(
        semester: String?,
        session: CourseSession,
        excludeCourseId: Long,
    ): List<Course> = coursesState.value
        .filter { c ->
            c.id != excludeCourseId && (semester == null || c.semester == semester)
        }
        .map { it.withSessionsFrom(sessionsState.value[it.id].orEmpty()) }
        .filter { course -> course.effectiveSessions.any { it.overlaps(session) } }

    // ===== Semester =====

    override fun observeAllSemesters(): Flow<List<Semester>> = semestersState
    override fun observeCurrentSemester(): Flow<Semester?> =
        semestersState.map { list -> list.firstOrNull { it.isCurrent } }
    override suspend fun getSemesterByName(name: String): Semester? =
        semestersState.value.firstOrNull { it.name == name }
    override suspend fun upsertSemester(semester: Semester) {
        semestersState.value = semestersState.value.filterNot { it.name == semester.name } + semester
    }
    override suspend fun deleteSemester(name: String) {
        semestersState.value = semestersState.value.filterNot { it.name == name }
    }

    // ===== Phase 8 Sub-step 3 全局搜索 =====

    /**
     * 测试用搜索实现：
     * - :keyword 形如 `%xxx%`，去掉首尾 `%` 后在 name / teacher / location / note 子串匹配
     * - 区分大小写
     */
    override fun searchCourses(keyword: String): Flow<List<Course>> =
        coursesState.map { list ->
            val needle = keyword.removePrefix("%").removeSuffix("%")
            list.filter { course ->
                course.name.contains(needle, ignoreCase = false) ||
                    course.teacher.contains(needle, ignoreCase = false) ||
                    course.location.contains(needle, ignoreCase = false) ||
                    (course.note?.contains(needle, ignoreCase = false) == true)
            }
        }

    // ===== 测试辅助 helpers =====

    suspend fun seedCourse(course: Course) {
        coursesState.value = coursesState.value + course
    }

    /** 给某门课塞课时（courseId 必须与课程 id 一致） */
    suspend fun seedSessions(courseId: Long, sessions: List<CourseSession>) {
        sessionsState.value = sessionsState.value + (
            courseId to sessions.mapIndexed { index, s ->
                s.copy(id = index + 1L, courseId = courseId, sortOrder = index)
            }
            )
    }

    /** 塞一个学期（Phase 28 P7：编辑页新建课程时需要学期） */
    suspend fun seedSemester(semester: Semester) {
        upsertSemester(semester)
    }

    /** 当前某门课的课时（未设置时返回空列表） */
    fun sessionsOf(courseId: Long): List<CourseSession> = sessionsState.value[courseId].orEmpty()

    fun clear() {
        coursesState.value = emptyList()
        sessionsState.value = emptyMap()
    }

    /**
     * 给课程挂上课时；**没有课时时用主时段镜像兜底出一个单课时**
     * （与生产实现的 [toDomainWithSessions] 语义一致）。
     */
    private fun Course.withSessionsFrom(sessions: List<CourseSession>): Course {
        val effective = sessions.ifEmpty {
            listOf(
                CourseSession(
                    courseId = id,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    sortOrder = 0,
                ),
            )
        }
        return copy(sessions = effective)
    }
}
