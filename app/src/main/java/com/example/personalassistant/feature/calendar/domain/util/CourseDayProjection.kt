package com.example.personalassistant.feature.calendar.domain.util

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession

/**
 * Phase 28 P6：课程「投影到某一天」
 *
 * ## 为什么需要投影
 *
 * `Course` 上的 `dayOfWeek / startTime / endTime` 三列是**主时段镜像**
 * （= 第一个课时的星期几与时间）。一门课有多个课时时，
 * 光看这三列**只知道它第一个课时在哪**——
 * 主时段在周一、还有一个课时在周三的课，在周三的日历上就「不见了」。
 *
 * 所以日历这一侧统一走「按课时查 → 投影到这一天」：
 * - [sessionsOn] 取这门课在这一天的**全部课时**
 * - [projectToDay] 把课程投影成「这一天的课程」：
 *   三列镜像改成**这一天的第一个课时**，课时列表也只留这一天的那些
 *
 * 投影之后，UI 层读 `course.startTime / endTime` 拿到的就是「这一天的课的时间」，
 * 不用再关心主时段镜像那套口径。
 */

/**
 * 一门课在指定星期的**全部课时**
 *
 * - 有课时记录 → 过滤出那一天的那些
 * - 没有课时记录（老数据 / 旧备份导入）→ [Course.effectiveSessions] 用主时段镜像兜底出一个，
 *   所以只有镜像那一天才匹配得上
 *
 * @param dayOfWeek 1=周一 … 7=周日
 */
fun Course.sessionsOn(dayOfWeek: Int): List<CourseSession> =
    effectiveSessions.filter { it.dayOfWeek == dayOfWeek }

/**
 * 把课程投影到某一天：一门课一条记录
 *
 * - 三列镜像（dayOfWeek / startTime / endTime）= **这一天的第一个课时**
 * - [Course.sessions] = 这一天的那些课时（渲染层按它一个课时画一个块）
 *
 * @param dayOfWeek 1=周一 … 7=周日
 * @return 这一天没有课 → null
 */
fun Course.projectToDay(dayOfWeek: Int): Course? {
    val daySessions = sessionsOn(dayOfWeek)
    if (daySessions.isEmpty()) return null
    val first = daySessions.minBy { it.startTime }
    return copy(
        dayOfWeek = dayOfWeek,
        startTime = first.startTime,
        endTime = first.endTime,
        sessions = daySessions,
    )
}

/**
 * 把一批课程投影到某一天（过滤掉那天没课的）
 */
fun List<Course>.projectToDay(dayOfWeek: Int): List<Course> =
    mapNotNull { it.projectToDay(dayOfWeek) }
