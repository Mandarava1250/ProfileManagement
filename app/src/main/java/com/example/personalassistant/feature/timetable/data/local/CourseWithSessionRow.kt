package com.example.personalassistant.feature.timetable.data.local

import androidx.room.Embedded

/**
 * 「课程 × 课时」的扁平行 —— 按天查课时的 JOIN 结果（Phase 28 P6）
 *
 * 一行 = **一门课的一个课时**：
 * - [course] 是课程本体（`courses` 表的一行）
 * - 后面的 `session*` 字段是 JOIN 出来的那个课时的信息
 *
 * 为什么不用 `@Relation`：关系查询会把该课程的**全部**课时都拉回来，
 * 没法只取「这一天的那几个课时」；这里要的就是「这一天」的口径。
 */
data class CourseWithSessionRow(
    /** 课程本体 */
    @Embedded
    val course: CourseEntity,
    /** 课时的数据库 id */
    val sessionId: Long,
    /** 课时的星期几（1=周一 … 7=周日） */
    val sessionDayOfWeek: Int,
    /** 课时的开始时间（当天分钟数，0-1439） */
    val sessionStartTime: Int,
    /** 课时的结束时间（当天分钟数，0-1439） */
    val sessionEndTime: Int,
    /** 课时在课程内的顺序（0 起） */
    val sessionSortOrder: Int,
)
