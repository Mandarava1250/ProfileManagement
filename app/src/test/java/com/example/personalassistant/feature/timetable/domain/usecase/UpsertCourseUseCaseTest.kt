package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * UpsertCourseUseCase 冲突检测算法测试
 *
 * 使用手写 fake repository（不引入 mockk），用 runBlocking 跑 suspend 代码
 */
class UpsertCourseUseCaseTest {

    private fun course(
        id: Long = 0L,
        name: String = "高数",
        dayOfWeek: Int = 1,
        startTime: Int = 540,
        endTime: Int = 600,
        semester: String? = "2024-2025-1",
    ) = Course(
        id = id,
        name = name,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        color = 0xFFE57373.toInt(),
        semester = semester,
    )

    /** 行为可控的最小 fake repo：记录插入的课程、按 conflict query 返回 */
    private class FakeTimetableRepository(
        private val existing: List<Course>,
    ) : TimetableRepository {

        val upserted = mutableListOf<Course>()
        var deletedIds = mutableListOf<Long>()

        override suspend fun findConflicts(
            semester: String?,
            dayOfWeek: Int,
            startTime: Int,
            endTime: Int,
            excludeId: Long,
        ): List<Course> = existing.filter { c ->
            c.id != excludeId &&
                (semester == null || c.semester == semester) &&
                c.dayOfWeek == dayOfWeek &&
                c.startTime < endTime &&
                c.endTime > startTime
        }

        override suspend fun upsertCourse(course: Course): Long {
            upserted += course
            return course.id
        }

        override suspend fun deleteCourse(id: Long) { deletedIds += id }
        override fun observeCoursesBySemester(semester: String): Flow<List<Course>> = flowOf(existing)
        override fun observeCoursesByDay(semester: String, dayOfWeek: Int): Flow<List<Course>> = flowOf(emptyList())
        override suspend fun getCourseById(id: Long): Course? = existing.firstOrNull { it.id == id }
        override suspend fun switchSemester(name: String) {}
        override fun observeAllSemesters(): Flow<List<Semester>> = flowOf(emptyList())
        override fun observeCurrentSemester(): Flow<Semester?> = flowOf(null)
        override suspend fun getSemesterByName(name: String): Semester? = null
        override suspend fun upsertSemester(semester: Semester) {}
        override suspend fun deleteSemester(name: String) {}

        // Phase 8 Sub-step 3 全局搜索（占位实现 — 此 UseCase 测试不关心搜索）
        override fun searchCourses(keyword: String): Flow<List<Course>> = flowOf(emptyList())
    }

    @Test
    fun `force=false with no conflict writes the course`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 2, startTime = 600, endTime = 660))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(id = 0L, dayOfWeek = 1, startTime = 540, endTime = 600)
        val result = useCase(newCourse)

        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, repo.upserted.size)
        val stored = repo.upserted[0]
        assertEquals(newCourse.name, stored.name)
        assertEquals(newCourse.dayOfWeek, stored.dayOfWeek)
        assertEquals(newCourse.startTime, stored.startTime)
        assertEquals(newCourse.endTime, stored.endTime)
        assertEquals(newCourse.semester, stored.semester)
    }

    @Test
    fun `force=false with conflict throws CourseConflictException`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(id = 0L, dayOfWeek = 1, startTime = 555, endTime = 615)
        try {
            useCase(newCourse)
            fail("expected CourseConflictException")
        } catch (e: CourseConflictException) {
            assertNotNull(e.conflict)
            assertTrue(e.conflict.hasConflict)
            assertEquals(1, e.conflict.conflicts.size)
        }
        assertEquals(0, repo.upserted.size)
    }

    @Test
    fun `force=true with conflict writes the course`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(id = 0L, dayOfWeek = 1, startTime = 555, endTime = 615)
        val result = useCase(newCourse, force = true)

        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, repo.upserted.size)
    }

    @Test
    fun `editing own course does not flag self as conflict`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val edited = course(id = 10L, dayOfWeek = 1, startTime = 555, endTime = 615)
        val result = useCase(edited)

        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, repo.upserted.size)
    }

    @Test
    fun `different day does not trigger conflict`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(id = 0L, dayOfWeek = 2, startTime = 540, endTime = 600)
        val result = useCase(newCourse)

        assertTrue(result.conflicts.isEmpty())
    }
}