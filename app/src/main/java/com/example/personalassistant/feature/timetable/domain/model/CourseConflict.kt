package com.example.personalassistant.feature.timetable.domain.model

/**
 * 课表课程冲突结果
 *
 * 上游在调用 UpsertCourseUseCase 时可以拿到这个对象，
 * 决定是否要弹"冲突提示"对话框。
 *
 * ## Phase 28：两类冲突
 *
 * 1. **跨课程冲突**（[conflicts]）—— 我的某个课时撞了别的课的某个课时。
 *    用 [sessionDetails] 可以知道「具体是哪个课时撞了谁」。
 * 2. **同课程内部冲突**（[internalOverlaps]）—— 同一门课的两个课时自己撞了。
 *    这类冲突**查不出来**：Repository 的冲突检测会把「被编辑课程自己」整个排除掉
 *    （不排除的话，改时间时旧值会跟自己撞），所以必须在 UseCase 里单独查。
 */
data class CourseConflict(
    val newCourse: Course,
    /** 与之冲突的其它课程（去重后） */
    val conflicts: List<Course>,
    /**
     * 冲突明细：我的哪个课时撞了哪门课的哪个课时（Phase 28 新增）
     *
     * 与 [conflicts] 是「一对多」：一门课可能被我的多个课时撞到，
     * [conflicts] 只列课程，这裡列具体课时对。
     */
    val sessionDetails: List<SessionConflict> = emptyList(),
    /**
     * 同一门课内部课时自相矛盾的明细（Phase 28 新增）
     *
     * 例如「周一 1-2 节」和「周一 1:30-3:00」两个课时自己重叠了。
     */
    val internalOverlaps: List<InternalSessionOverlap> = emptyList(),
) {
    /** 有没有跨课程冲突 */
    val hasConflict: Boolean get() = conflicts.isNotEmpty()

    /** 有没有同课程内部课时重叠 */
    val hasInternalOverlap: Boolean get() = internalOverlaps.isNotEmpty()

    /** 有没有任何一种冲突（跨课程 + 内部重叠） */
    val hasAnyConflict: Boolean get() = hasConflict || hasInternalOverlap
}

/**
 * 一条跨课程冲突明细
 *
 * @param session 本门课（newCourse）的哪个课时
 * @param conflictingCourse 撞上的那门课
 * @param conflictingSession 那门课的哪个课时（没有课时记录时是主时段镜像兜底出来的）
 */
data class SessionConflict(
    val session: CourseSession,
    val conflictingCourse: Course,
    val conflictingSession: CourseSession,
)

/**
 * 同一门课内部的课时重叠
 *
 * @param first 第一个课时
 * @param second 与它重叠的另一个课时
 */
data class InternalSessionOverlap(
    val first: CourseSession,
    val second: CourseSession,
)
