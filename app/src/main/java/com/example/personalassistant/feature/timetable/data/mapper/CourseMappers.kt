package com.example.personalassistant.feature.timetable.data.mapper

import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.CourseSessionEntity
import com.example.personalassistant.feature.timetable.data.local.CourseWithSessionRow
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
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

/**
 * 把「课程行 + 它的课时行」合成一个领域 [Course]（Phase 28 P4）
 *
 * [CourseEntity.toDomain] 只映射 `courses` 表自身的字段（课时不在那张表里），
 * 合并逻辑放在这里 —— Repository 读路径统一走这个函数。
 *
 * ## ⚠️ 空课时的兜底
 *
 * 课程在 `course_sessions` 里没有记录时（老数据没播种 / 导入的旧备份），
 * 用 `courses` 表的三列镜像**兜底出一个单课时** ——
 * 保证渲染层永远至少看到一个课时，一门课不会从课表上凭空消失。
 */
fun CourseEntity.toDomainWithSessions(sessions: List<CourseSessionEntity>): Course {
    val domainSessions = sessions.map { it.toDomain() }
    val effective = domainSessions.ifEmpty {
        listOf(
            CourseSession(
                courseId = id,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                sortOrder = 0,
                // Phase 28 P10：兜底课时代表的就是「这门课的那个时段」，
                // 教师 / 地点自然跟课程本体一致
                teacher = teacher,
                location = location,
            ),
        )
    }
    return toDomain().copy(sessions = effective)
}

/**
 * 课时 Entity ↔ Domain 互转（Phase 28 P1）
 *
 * 与课程同款：顶层扩展函数，方便用
 * `import com.example.personalassistant.feature.timetable.data.mapper.toDomain`
 */
fun CourseSessionEntity.toDomain(): CourseSession = CourseSession(
    id = id,
    courseId = courseId,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    sortOrder = sortOrder,
    // Phase 28 P10：课时自己的教师 / 地点（空串 = 沿用课程级）
    teacher = teacher,
    location = location,
)

/**
 * 把「课程 × 课时」的 JOIN 行拆成领域课时（Phase 28 P6）
 *
 * `courseId` 取课程本体的 id —— JOIN 条件就是 `s.courseId = c.id`，两者必然一致。
 */
fun CourseWithSessionRow.toDomainSession(): CourseSession = CourseSession(
    id = sessionId,
    courseId = course.id,
    dayOfWeek = sessionDayOfWeek,
    startTime = sessionStartTime,
    endTime = sessionEndTime,
    sortOrder = sessionSortOrder,
    // Phase 28 P10：JOIN 出来的课时自带教师 / 地点
    teacher = sessionTeacher,
    location = sessionLocation,
)

fun CourseSession.toEntity(): CourseSessionEntity = CourseSessionEntity(
    id = id,
    courseId = courseId,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    sortOrder = sortOrder,
    teacher = teacher,
    location = location,
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