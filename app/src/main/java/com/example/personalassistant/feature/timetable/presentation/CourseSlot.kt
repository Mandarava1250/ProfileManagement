package com.example.personalassistant.feature.timetable.presentation

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession

/**
 * 课表上要画的一个块 = 一门课的一个课时
 *
 * ## 为什么是「课时」而不是「课程」
 *
 * 改造前：一门课 = 一个上课时段（星期几 + 起止时间都长在课程身上）→ 课表上一门课画一个块。
 * 改造后（Phase 28）：一门课 = 课程本体 + N 个 [CourseSession]
 * （例：高等数学每周一 1-2 节、周三 3-4 节、周五 5-6 节），
 * 课表上**每个课时各占一块**。
 *
 * 同一门课的多个块用**同一个课程色**（取 [course] 的颜色），
 * 用户一眼能看出「这几个块是同一门课」——
 * 这是「一门课多课时」在视觉上唯一可靠的线索（块之间并不相连）。
 *
 * ## 点击行为
 *
 * 点任意一块都进**整门课**的编辑页（路由靠 [course] 的 id），
 * 而不是「只改这个课时」—— 那是批次 C（课程编辑页）的范围。
 *
 * @param course 这块属于哪门课
 * @param session 这块对应哪个课时（决定画在哪、画多高）
 */
data class CourseSlot(
    val course: Course,
    val session: CourseSession,
)

/**
 * 把课程列表摊平成课时块（Phase 28 P5）
 *
 * - 先按周次规则过滤课程（[Course.appliesToWeek]）—— 这一周不上课的课不占格子
 * - 再把每门课摊平成它的各个课时（[Course.effectiveSessions]）
 *   —— 没有课时记录的老数据由 `effectiveSessions` 用主时段镜像兜底出一个课时，
 *   所以**一门课至少占一块**，不会从课表上凭空消失
 * - 排序：先按星期的星期几，再按开始时间
 *
 * 抽成顶层函数是为了能直接写 JVM 单测（渲染层的 Compose 代码测不了）。
 */
fun List<Course>.toCourseSlots(weekNumber: Int): List<CourseSlot> =
    filter { it.appliesToWeek(weekNumber) }
        .flatMap { course ->
            course.effectiveSessions.map { session -> CourseSlot(course, session) }
        }
        .sortedWith(compareBy({ it.session.dayOfWeek }, { it.session.startTime }))
