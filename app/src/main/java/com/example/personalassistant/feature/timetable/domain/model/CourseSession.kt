package com.example.personalassistant.feature.timetable.domain.model

/**
 * 课时领域模型 —— 「一门课」下面的**一个上课时段**
 *
 * ## 它和 [Course] 的关系
 *
 * 改造前：一门课 = 一个上课时段（星期几 + 起止时间都长在课程身上）。
 * 改造后：**一门课 = 课程本体 + 若干个课时**。
 *
 * 举例：高等数学每周一 1-2 节、周三 3-4 节、周五 5-6 节 ——
 * 这是**一门**课（名字/老师/地点/颜色/学期/周次都一样），但有**三个**上课时段，
 * 每个时段有自己的星期几和起止时间。这三个时段就是三个 [CourseSession]。
 *
 * 与 [CourseSessionEntity] 不同：
 *   - 业务语义清晰，不含数据库注解
 *   - 提供区间相交判断（[overlaps]）等业务方法
 *   - 与 UI 层之间通过 mapper 转换
 *
 * ## 时间口径
 * - [dayOfWeek]：1=周一 … 7=周日（与 [Course] 同一套口径）
 * - [startTime] / [endTime]：当天分钟数（0-1439），与具体日期无关，避免时区干扰
 *
 * @param id 数据库主键；新建时为 0
 * @param courseId 所属课程 id（[Course.id]）；新建时为 0
 * @param dayOfWeek 星期几，1=周一，7=周日
 * @param startTime 开始时间（当天分钟数，0-1439）
 * @param endTime 结束时间（当天分钟数，0-1439，且 > startTime）
 * @param sortOrder 课程内的显示顺序（0 起）；同一门课的课时按它排序
 */
data class CourseSession(
    val id: Long = 0L,
    val courseId: Long = 0L,
    val dayOfWeek: Int,
    val startTime: Int,
    val endTime: Int,
    val sortOrder: Int = 0,
) {
    init {
        require(dayOfWeek in 1..7) { "dayOfWeek 必须在 1..7" }
        require(startTime in 0..1439) { "startTime 必须在 0..1439" }
        require(endTime in 0..1439) { "endTime 必须在 0..1439" }
        require(endTime > startTime) { "endTime 必须大于 startTime" }
    }

    /** 课时时长（分钟） */
    val durationMinutes: Int get() = endTime - startTime

    /**
     * 与另一课时是否在同一天且时间区间相交
     *
     * - 同一天（dayOfWeek 相同）
     * - 时间区间有交集：startA < endB && endA > startB
     *   （首尾相接不算相交 —— 08:00-09:40 与 09:40-11:00 可以连排）
     *
     * ⚠️ 这里**不含**「是不是同一门课」的判断 —— 同一门课的两个课时互相撞了也是错，
     * 但那属于「课程内部自相矛盾」，由上层单独报（见方案里的冲突检测）。
     */
    fun overlaps(other: CourseSession): Boolean =
        dayOfWeek == other.dayOfWeek && startTime < other.endTime && endTime > other.startTime

    companion object {
        /** 一天最多 1440 分钟 */
        const val MINUTES_PER_DAY = 1440
    }
}
