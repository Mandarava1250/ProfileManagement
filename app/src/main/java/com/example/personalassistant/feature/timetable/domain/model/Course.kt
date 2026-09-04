package com.example.personalassistant.feature.timetable.domain.model

/**
 * 课程领域模型
 *
 * 与 CourseEntity 不同：
 *   - 业务语义清晰，不含数据库注解
 *   - 提供 weeks 解析和冲突判断等业务方法
 *   - 与 UI 层之间通过 mapper 转换
 *
 * @param id 数据库主键；新建时为 0
 * @param name 课程名称
 * @param teacher 教师
 * @param location 上课地点
 * @param dayOfWeek 星期几，1=周一，7=周日
 * @param startTime 开始时间（当天分钟数，0-1439）
 * @param endTime 结束时间（当天分钟数，0-1439，且 > startTime）
 * @param color ARGB 颜色值，对应调色板中的索引或自由色
 * @param semester 所属学期名称（与 SemesterEntity.name 对齐）；null 表示未指定
 * @param weeks 周次规则（null 表示全学期）
 * @param reminderMinutes 课前提醒分钟数；0 表示不提醒
 * @param note 备注
 * @param createdAt 创建时间戳
 * @param updatedAt 更新时间戳
 */
data class Course(
    val id: Long = 0L,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int,
    val startTime: Int,
    val endTime: Int,
    val color: Int,
    val semester: String? = null,
    val weeks: String? = null,
    val reminderMinutes: Int = 15,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(name.isNotBlank()) { "课程名称不能为空" }
        require(dayOfWeek in 1..7) { "dayOfWeek 必须在 1..7" }
        require(startTime in 0..1439) { "startTime 必须在 0..1439" }
        require(endTime in 0..1439) { "endTime 必须在 0..1439" }
        require(endTime > startTime) { "endTime 必须大于 startTime" }
        require(reminderMinutes in 0..720) { "reminderMinutes 必须在 0..720" }
    }

    /**
     * 当前课程是否在指定周次上课
     *
     * weeks 规则：
     *   - null / ""           全学期都上课
     *   - "1-16"              1 到 16 周都上课
     *   - "1,3,5"             第 1、3、5 周上课
     *   - "1-8,10-16"         1-8 周以及 10-16 周上课
     */
    fun appliesToWeek(weekNumber: Int): Boolean {
        if (weeks.isNullOrBlank()) return true
        val rule = weeks.trim()
        if (rule.isEmpty()) return true
        return rule.split(',').any { part ->
                val token = part.trim()
                if (token.contains('-')) {
                    val (from, to) = token.split('-').map { it.trim().toIntOrNull() ?: return@any false }
                    weekNumber in from..to
                } else {
                    token.toIntOrNull() == weekNumber
                }
            }
    }

    /**
     * 判断与另一门课在同一学期内是否时间冲突
     *
     * - 同一天 (dayOfWeek)
     * - 时间区间有交集：startA < endB && endA > startB
     * - id 不同（编辑同一门课时不算冲突）
     */
    fun conflictsWith(other: Course): Boolean {
        if (id != 0L && id == other.id) return false
        if (dayOfWeek != other.dayOfWeek) return false
        if (semester != other.semester) return false
        return startTime < other.endTime && endTime > other.startTime
    }

    /** 课程时长（分钟）*/
    val durationMinutes: Int get() = endTime - startTime

    /** 是否需要提醒 */
    val hasReminder: Boolean get() = reminderMinutes > 0

    companion object {
        /** 一天最多 1440 分钟 */
        const val MINUTES_PER_DAY = 1440
    }
}