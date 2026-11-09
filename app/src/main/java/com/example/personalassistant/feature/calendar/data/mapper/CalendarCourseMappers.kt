package com.example.personalassistant.feature.calendar.data.mapper

import com.example.personalassistant.feature.calendar.domain.util.projectToDay
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.CourseWithSessionRow
import com.example.personalassistant.feature.timetable.data.mapper.toDomain
import com.example.personalassistant.feature.timetable.data.mapper.toDomainSession
import com.example.personalassistant.feature.timetable.domain.model.Course

/**
 * Phase 28 P6：把「课程 × 课时」的查询结果组装成**某一天的课程列表**
 *
 * ## 两个数据源
 *
 * - [joinedRows]：JOIN `course_sessions` 查出来的「这一天有课时的课程 + 它的那些课时」
 * - [mirroredCourses]：按主时段镜像查出来的课程 —— 用来兜底**没有课时记录的老课程**
 *   （老数据 / 旧备份导入的课，课时表里没有它的行）
 *
 * 两边按课程 id 合并：JOIN 出来的优先（它带真实课时），镜像的只补 JOIN 没覆盖到的。
 *
 * ## 返回的语义
 *
 * 一门课一条记录（`DayDetail.courses` 的口径），但
 * - 课程的三列镜像 = **这一天的第一个课时**
 * - 课程的 `sessions` = **这一天的那些课时**
 *
 * 这样 UI 层读 `course.startTime` 拿到的就是「这一天的课的时间」，
 * 按课时画块则读 `course.sessions`。
 *
 * @param joinedRows 按天 JOIN 出来的「课程 × 课时」行
 * @param mirroredCourses 按主时段镜像查出来的课程（老数据兜底）
 * @param dayOfWeek 1=周一 … 7=周日
 */
fun buildDayCourses(
    joinedRows: List<CourseWithSessionRow>,
    mirroredCourses: List<CourseEntity>,
    dayOfWeek: Int,
): List<Course> {
    // JOIN 出来的：一门课一个课时一行 → 按课程 id 分组成「这门课当天的课时」
    val rowsByCourse = joinedRows.groupBy { it.course.id }
    val fromJoin = rowsByCourse.values.map { rows ->
        rows.first().course.toDomain().copy(
            sessions = rows.map { it.toDomainSession() },
        )
    }

    // 老数据兜底：主时段镜像 == 这一天、且课时表里没有记录 → 用镜像兜底出单课时
    val fromMirror = mirroredCourses
        .filter { it.id !in rowsByCourse.keys }
        .map { it.toDomain() }

    return (fromJoin + fromMirror)
        .mapNotNull { it.projectToDay(dayOfWeek) }
        .sortedBy { it.startTime }
}

/**
 * 把「课程 × 课时」的 JOIN 结果按星期几分桶（每门课一天一条）
 *
 * 月汇总用：一天有几门课 = 那天有几门课**有课时**（同一门课一天两个课时只算一门）。
 *
 * @param joinedRows 按天 JOIN 出来的「课程 × 课时」行
 * @param mirroredCourses 按主时段镜像查出来的课程（老数据兜底）
 * @return 星期几 → 当天的课程（domain）
 */
fun buildCoursesByDay(
    joinedRows: List<CourseWithSessionRow>,
    mirroredCourses: List<CourseEntity>,
): Map<Int, List<Course>> {
    val coursesById = (joinedRows.map { it.course } + mirroredCourses).associateBy { it.id }

    // 先按「这门课在哪些天有课时」分桶
    val courseIdsByDay = (1..7).associateWith { mutableSetOf<Long>() }
    joinedRows.forEach { row ->
        courseIdsByDay[row.sessionDayOfWeek]?.add(row.course.id)
    }
    // 老数据（课时表里没有记录）→ 按主时段镜像归到那天
    val joinedIds = joinedRows.map { it.course.id }.toSet()
    mirroredCourses.filter { it.id !in joinedIds }.forEach { course ->
        courseIdsByDay[course.dayOfWeek]?.add(course.id)
    }

    return courseIdsByDay.mapValues { (_, courseIds) ->
        courseIds.mapNotNull { coursesById[it] }.map { it.toDomain() }.sortedBy { it.startTime }
    }
}
