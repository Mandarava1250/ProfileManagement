package com.example.personalassistant.feature.timetable.data.mapper

import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.Semester

/**
 * 课程 Entity ↔ Domain 互转
 *
 * 顶层扩展函数，方便用 `import com.example.personalassistant.feature.timetable.data.mapper.toDomain`
 */
fun CourseEntity.toDomain(): Course = Course(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    color = color,
    semester = semester,
    weeks = weeks,
    reminderMinutes = reminderMinutes,
    note = note,
    credit = credit,
    gradePoint = gradePoint,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Course.toEntity(): CourseEntity = CourseEntity(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    color = color,
    semester = semester,
    weeks = weeks,
    reminderMinutes = reminderMinutes,
    note = note,
    credit = credit,
    gradePoint = gradePoint,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SemesterEntity.toDomain(): Semester = Semester(
    name = name,
    startDate = startDate,
    endDate = endDate,
    isCurrent = isCurrent,
    createdAt = createdAt,
)

fun Semester.toEntity(): SemesterEntity = SemesterEntity(
    name = name,
    startDate = startDate,
    endDate = endDate,
    isCurrent = isCurrent,
    createdAt = createdAt,
)