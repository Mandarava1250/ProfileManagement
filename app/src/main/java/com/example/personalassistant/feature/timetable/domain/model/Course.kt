package com.example.personalassistant.feature.timetable.domain.model

/**
 * 课程领域模型
 *
 * 与 CourseEntity 不同：
 *   - 业务语义清晰，不含数据库注解
 *   - 提供 weeks 解析和冲突判断等业务方法
 *   - 与 UI 层之间通过 mapper 转换
 *
 * ## 一门课 = 课程本体 + 若干个课时（Phase 28）
 *
 * 改造前：一门课 = 一个上课时段（星期几 + 起止时间都长在课程身上）。
 * 改造后：**一门课 = 课程本体 + N 个 [CourseSession]**。
 * 例：高等数学每周一 1-2 节、周三 3-4 节、周五 5-6 节 —— 一门课，三个课时。
 *
 * @param id 数据库主键；新建时为 0
 * @param name 课程名称
 * @param teacher 教师
 * @param location 上课地点
 * @param dayOfWeek **主时段镜像**（= 第一个课时的星期几），1=周一，7=周日
 * @param startTime **主时段镜像**（= 第一个课时的开始时间，当天分钟数，0-1439）
 * @param endTime **主时段镜像**（= 第一个课时的结束时间，当天分钟数，0-1439，且 > startTime）
 * @param color ARGB 颜色值，对应调色板中的索引或自由色
 * @param semester 所属学期名称（与 SemesterEntity.name 对齐）；null 表示未指定
 * @param weeks 周次规则（null 表示全学期）
 * @param reminderMinutes 课前提醒分钟数；0 表示不提醒
 * @param note 备注
 * @param sessions 这门课的**全部课时**；空列表表示「还没建课时」
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
    /** 学分（Phase 12 新增；null = 未填） */
    val credit: Double? = null,
    /** 绩点（0.0-4.0；Phase 12 新增；null = 未填） */
    val gradePoint: Double? = null,
    /**
     * 这门课的**全部课时**（Phase 28 新增）
     *
     * 默认空列表 —— 这样现有所有构造 `Course(...)` 的地方（测试、日历、提醒）
     * 不用立刻改就能编译。空列表在语义上表示「这门课只有一个时段，
     * 就是主时段镜像」，由 [effectiveSessions] 兜底。
     */
    val sessions: List<CourseSession> = emptyList(),
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
     * **实际生效**的课时列表
     *
     * 渲染层永远至少看到一个课时：
     * - 有课时记录 → 就是它们
     * - 没有课时记录（老数据没播种 / 导入的旧备份）→ 用主时段镜像兜底出一个单课时
     *
     * ⚠️ 这条兜底很重要：一门课在课表上必须至少占一个格子，
     * 「没有课时」不能让整门课从课表上消失。
     */
    val effectiveSessions: List<CourseSession>
        get() = sessions.ifEmpty {
            listOf(
                CourseSession(
                    courseId = id,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    sortOrder = 0,
                ),
            )
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
     * 判断与另一门课在同一学期内是否时间冲突（Phase 28：改成按课时判断）
     *
     * ## 新语义
     *
     * 「冲突」= **我的某个课时**与**对方的某个课时**时间重叠
     * （同一天 + 区间有交集；首尾相接不算）。
     *
     * 与改造前的区别：改造前只看课程身上的那一个时段，
     * 现在一门课有三个时段时，跟别的课撞的是**每一个**时段。
     *
     * - 没有课时记录的一方 → 用主时段镜像兜底出一个单课时（见 [effectiveSessions]）
     * - id 相同（编辑同一门课）→ 不算冲突
     * - 学期不同 → 不算冲突（跨学期的冲突由 Repository 层按「未归学期 = 撞所有学期」的口径处理）
     */
    fun conflictsWith(other: Course): Boolean {
        if (id != 0L && id == other.id) return false
        if (semester != other.semester) return false
        val mine = effectiveSessions
        val theirs = other.effectiveSessions
        return mine.any { a -> theirs.any { b -> a.overlaps(b) } }
    }

    /** **主时段**时长（分钟）—— 按主时段镜像算，不是所有课时之和 */
    val durationMinutes: Int get() = endTime - startTime

    /** 是否需要提醒 */
    val hasReminder: Boolean get() = reminderMinutes > 0

    companion object {
        /** 一天最多 1440 分钟 */
        const val MINUTES_PER_DAY = 1440
    }
}
