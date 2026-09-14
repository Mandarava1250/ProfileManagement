package com.example.personalassistant.feature.timetable.data.repository

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Phase 8 Sub-step 3：单元测试用的最小 [TimetableRepository] stub
 *
 * 只实现 [searchCourses] + 必要的 observe 兜底。其他方法全部返回默认值，
 * 因为 Sub-step 3 测试只需要 search 路径。
 */
class FakeTimetableRepository : TimetableRepository {

    private val coursesState = MutableStateFlow<List<Course>>(emptyList())

    var upsertCount: Int = 0
        private set

    // ===== Course =====

    override fun observeCoursesBySemester(semester: String): Flow<List<Course>> =
        coursesState.map { list -> list.filter { it.semester == semester } }

    override fun observeCoursesByDay(semester: String, dayOfWeek: Int): Flow<List<Course>> =
        coursesState.map { list -> list.filter { it.semester == semester && it.dayOfWeek == dayOfWeek } }

    override suspend fun getCourseById(id: Long): Course? = null

    override suspend fun findConflicts(
        semester: String?,
        dayOfWeek: Int,
        startTime: Int,
        endTime: Int,
        excludeId: Long,
    ): List<Course> = emptyList()

    override suspend fun upsertCourse(course: Course): Long {
        upsertCount++
        val resolved = if (course.id > 0L) course else course.copy(id = (coursesState.value.size + 1).toLong())
        coursesState.value = coursesState.value + resolved
        return resolved.id
    }

    override suspend fun deleteCourse(id: Long) {
        coursesState.value = coursesState.value.filter { it.id != id }
    }

    override suspend fun switchSemester(name: String) = Unit

    // ===== Semester =====

    override fun observeAllSemesters(): Flow<List<Semester>> = MutableStateFlow(emptyList())
    override fun observeCurrentSemester(): Flow<Semester?> = MutableStateFlow(null)
    override suspend fun getSemesterByName(name: String): Semester? = null
    override suspend fun upsertSemester(semester: Semester) = Unit
    override suspend fun deleteSemester(name: String) = Unit

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

    fun clear() {
        coursesState.value = emptyList()
    }
}
