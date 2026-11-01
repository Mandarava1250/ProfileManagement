package com.example.personalassistant.feature.timetable.domain.model

/**
 * 课表课程冲突结果
 *
 * 上游在调用 UpsertCourseUseCase 时可以拿到这个对象，
 * 决定是否要弹"冲突提示"对话框。
 */
data class CourseConflict(
    val newCourse: Course,
    val conflicts: List<Course>,
) {
    val hasConflict: Boolean get() = conflicts.isNotEmpty()
}