package com.example.personalassistant.feature.me.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Course
import javax.inject.Inject

/**
 * 计算 GPA（4.0 制）
 *
 * ## 公式
 * `GPA = Σ(credit × gradePoint) / Σ(credit)`
 *
 * ## 排除规则
 * - **当前学期**的课不参与计算（在修还没出分，混进来会把均分拉低）
 * - `credit` 为 `null`（未填）的课不参与
 * - `gradePoint` 为 `null`（未填）的课不参与
 * - `gradePoint` 越界（< 0 或 > 4.0）的课不参与
 * - `credit` ≤ 0 的课不参与
 *
 * ## 边界
 * - 没有任何课贡献有效数据时返回 `null`（UI 显示"暂无成绩"）
 * - 至少一门课有效才返回计算结果
 *
 * ## 入参
 * @param courses 用户所有课（不限学期）；内部按 `excludedSemester` 过滤
 * @param excludedSemester 要排除的学期名（通常传当前学期 `Semester.isCurrent` 的那个）；
 *        `null` 表示"不按学期排除"（如学期为空的早期用户）
 */
class CalculateGpaUseCase @Inject constructor() {

    operator fun invoke(
        courses: List<Course>,
        excludedSemester: String?,
    ): GpaSummary? {
        val eligible = courses.asSequence()
            .filter { course -> excludedSemester == null || course.semester != excludedSemester }
            .mapNotNull { it.toEligiblePair() }
            .toList()

        if (eligible.isEmpty()) return null

        val totalCredits: Double = eligible.sumOf { it.first }
        if (totalCredits == 0.0) return null

        val weightedSum: Double = eligible.sumOf { it.first * it.second }
        val gpa = weightedSum / totalCredits

        return GpaSummary(
            gpa = gpa,
            totalCredits = totalCredits,
            courseCount = eligible.size,
        )
    }

    /**
     * 把课程转成可纳入计算的 (credit, gradePoint) 二元组
     *
     * 不满足下列任一条件返回 `null`：
     *  - credit 不为 null 且 > 0
     *  - gradePoint 不为 null 且在 [0.0, 4.0] 内
     */
    private fun Course.toEligiblePair(): Pair<Double, Double>? {
        val c = credit ?: return null
        val g = gradePoint ?: return null
        if (c <= 0.0) return null
        if (g < 0.0 || g > MAX_GRADE_POINT) return null
        return c to g
    }

    companion object {
        const val MAX_GRADE_POINT = 4.0
    }
}

/**
 * GPA 汇总结果
 *
 * @param gpa 加权 GPA（已乘 Σcredit / Σcredit）
 * @param totalCredits 参与计算的总学分
 * @param courseCount 参与计算的课程数
 */
data class GpaSummary(
    val gpa: Double,
    val totalCredits: Double,
    val courseCount: Int,
)
