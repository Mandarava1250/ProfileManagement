package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseConflict
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import javax.inject.Inject

/**
 * 新增或更新课程
 *
 * 流程：
 *  1. 先查冲突（按 dayOfWeek + semester + 时间区间，排除自身 id）
 *  2. 若 force = false 且存在冲突，抛出异常，由上层决定是否提示
 *  3. 写入数据库（id=0 自动新增，否则覆盖更新）
 *  4. 返回 CourseConflict 以便上层展示提示
 *
 * @param force true = 忽略冲突强制写入；false = 有冲突则抛 CourseConflictException
 */
class UpsertCourseUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    suspend operator fun invoke(course: Course, force: Boolean = false): CourseConflict {
        val conflicts = repository.findConflicts(
            semester = course.semester,
            dayOfWeek = course.dayOfWeek,
            startTime = course.startTime,
            endTime = course.endTime,
            excludeId = course.id,
        )
        if (conflicts.isNotEmpty() && !force) {
            throw CourseConflictException(CourseConflict(course, conflicts))
        }
        repository.upsertCourse(course.copy(updatedAt = System.currentTimeMillis()))
        return CourseConflict(course.copy(updatedAt = System.currentTimeMillis()), emptyList())
    }
}

/** 课程冲突异常：上层捕获后可弹出"是否继续"对话框 */
class CourseConflictException(val conflict: CourseConflict) : Exception(
    "课程与 ${conflict.conflicts.size} 门已有课程时间冲突"
)