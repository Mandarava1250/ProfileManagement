package com.example.personalassistant.feature.me.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateGpaUseCaseTest {

    private val useCase = CalculateGpaUseCase()

    @Test
    fun `空课表返回 null`() {
        assertNull(useCase(emptyList(), excludedSemester = null))
        assertNull(useCase(emptyList(), excludedSemester = "2025-秋"))
    }

    @Test
    fun `排除当前学期 - 当前学期课不算`() {
        val courses = listOf(
            course(name = "高数", semester = "2024-春", credit = 4.0, gradePoint = 3.0),
            course(name = "大物", semester = "2025-秋", credit = 3.0, gradePoint = 4.0), // 当前学期
        )
        // 高数 4×3 = 12
        val result = useCase(courses, excludedSemester = "2025-秋")
        assertNotNull(result)
        assertEquals(3.0, result!!.gpa, 1e-9)
        assertEquals(4.0, result.totalCredits, 1e-9)
        assertEquals(1, result.courseCount)
    }

    @Test
    fun `加权平均 - 不同学分的权重`() {
        val courses = listOf(
            course(name = "A", semester = "2024-春", credit = 2.0, gradePoint = 4.0), // 8
            course(name = "B", semester = "2024-春", credit = 3.0, gradePoint = 3.0), // 9
        )
        // (8 + 9) / 5 = 3.4
        val result = useCase(courses, excludedSemester = null)
        assertNotNull(result)
        assertEquals(3.4, result!!.gpa, 1e-9)
        assertEquals(5.0, result.totalCredits, 1e-9)
        assertEquals(2, result.courseCount)
    }

    @Test
    fun `credit 为 null 不参与计算`() {
        val courses = listOf(
            course(name = "A", semester = "2024-春", credit = null, gradePoint = 4.0),
            course(name = "B", semester = "2024-春", credit = 3.0, gradePoint = 3.0),
        )
        // 只有 B：3.0
        val result = useCase(courses, excludedSemester = null)
        assertNotNull(result)
        assertEquals(3.0, result!!.gpa, 1e-9)
        assertEquals(3.0, result.totalCredits, 1e-9)
    }

    @Test
    fun `gradePoint 为 null 不参与计算`() {
        val courses = listOf(
            course(name = "A", semester = "2024-春", credit = 2.0, gradePoint = null),
            course(name = "B", semester = "2024-春", credit = 3.0, gradePoint = 3.0),
        )
        val result = useCase(courses, excludedSemester = null)
        assertNotNull(result)
        assertEquals(3.0, result!!.gpa, 1e-9)
    }

    @Test
    fun `gradePoint 越界不参与计算`() {
        val courses = listOf(
            course(name = "A", semester = "2024-春", credit = 2.0, gradePoint = 4.5), // 越界
            course(name = "B", semester = "2024-春", credit = 3.0, gradePoint = 3.0),
            course(name = "C", semester = "2024-春", credit = 2.0, gradePoint = -1.0), // 越界
        )
        val result = useCase(courses, excludedSemester = null)
        assertNotNull(result)
        assertEquals(3.0, result!!.gpa, 1e-9)
        assertEquals(3.0, result.totalCredits, 1e-9)
    }

    @Test
    fun `credit 小于等于 0 不参与计算`() {
        val courses = listOf(
            course(name = "A", semester = "2024-春", credit = 0.0, gradePoint = 4.0), // 0 学分
            course(name = "B", semester = "2024-春", credit = -1.0, gradePoint = 4.0), // 负学分
            course(name = "C", semester = "2024-春", credit = 3.0, gradePoint = 3.0),
        )
        val result = useCase(courses, excludedSemester = null)
        assertNotNull(result)
        assertEquals(3.0, result!!.gpa, 1e-9)
    }

    @Test
    fun `全部课程无效时返回 null`() {
        val courses = listOf(
            course(name = "A", semester = "2025-秋", credit = 4.0, gradePoint = 3.0), // 被排除
            course(name = "B", semester = "2024-春", credit = null, gradePoint = 4.0), // 缺 credit
        )
        assertNull(useCase(courses, excludedSemester = "2025-秋"))
    }

    @Test
    fun `excludedSemester 为 null 时不按学期排除`() {
        val courses = listOf(
            course(name = "A", semester = "2025-秋", credit = 4.0, gradePoint = 3.0),
        )
        val result = useCase(courses, excludedSemester = null)
        assertNotNull(result)
        assertEquals(3.0, result!!.gpa, 1e-9)
    }

    @Test
    fun `满绩 4_0 边界正确`() {
        val courses = listOf(
            course(name = "A", semester = "2024-春", credit = 2.0, gradePoint = 4.0),
            course(name = "B", semester = "2024-春", credit = 2.0, gradePoint = 4.0),
        )
        val result = useCase(courses, excludedSemester = null)
        assertNotNull(result)
        assertEquals(4.0, result!!.gpa, 1e-9)
    }

    // ===== 辅助工厂 =====
    private fun course(
        name: String = "默认课",
        semester: String? = "2024-春",
        credit: Double? = 3.0,
        gradePoint: Double? = 3.0,
    ): Course = Course(
        id = name.hashCode().toLong(),
        name = name,
        teacher = "",
        location = "",
        dayOfWeek = 1,
        startTime = 480,
        endTime = 540,
        color = 0,
        semester = semester,
        weeks = null,
        reminderMinutes = 15,
        note = null,
        credit = credit,
        gradePoint = gradePoint,
    )
}
