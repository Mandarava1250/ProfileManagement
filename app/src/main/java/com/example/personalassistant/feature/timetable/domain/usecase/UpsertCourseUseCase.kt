package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseConflict
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import com.example.personalassistant.feature.timetable.domain.model.InternalSessionOverlap
import com.example.personalassistant.feature.timetable.domain.model.SessionConflict
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import javax.inject.Inject

/**
 * 新增或更新课程
 *
 * ## Phase 28：逐课时冲突检测
 *
 * 流程：
 *  1. **同门课内部重叠检查** —— 本门课的两个课时自己撞了（见下方说明）
 *  2. **逐课时跨课程冲突检查** —— 遍历 `course.sessions`，任一课时撞了别的课就记一条
 *  3. 若 force = false 且存在任何冲突，抛出异常，由上层决定是否提示
 *  4. 整门课一次性写入（`upsertCourseWithSessions`：课程 + 课时 + 三列镜像，一个事务）
 *  5. 返回 CourseConflict 以便上层展示提示
 *
 * ## ⚠️ 为什么同门课内部重叠要单独查
 *
 * Repository 的冲突检测会把「被编辑课程自己」整个排除掉
 * （不排除的话，改时间时旧值会跟自己撞）。
 * 代价是：**同一门课的两个课时互相重叠**这类冲突查不出来 ——
 * 只能在这里自己查（对 `course.sessions` 两两比 [CourseSession.overlaps]）。
 *
 * @param force true = 忽略冲突强制写入；false = 有冲突则抛 CourseConflictException
 */
class UpsertCourseUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    suspend operator fun invoke(course: Course, force: Boolean = false): CourseConflict {
        // 没有课时记录时，用主时段镜像兜底出一个单课时 —— 语义上「这门课只有一个时段」
        val sessions = course.effectiveSessions

        // ===== 1. 同门课内部课时重叠 =====
        val internalOverlaps = findInternalOverlaps(sessions)

        // ===== 2. 逐课时跨课程冲突 =====
        val details = mutableListOf<SessionConflict>()
        val conflictsById = linkedMapOf<Long, Course>()
        sessions.forEach { session ->
            repository.findSessionConflicts(
                semester = course.semester,
                session = session,
                excludeCourseId = course.id,
            ).forEach { other ->
                conflictsById[other.id] = other
                val theirSession = other.effectiveSessions.firstOrNull { it.overlaps(session) }
                    ?: other.effectiveSessions.first()
                details += SessionConflict(
                    session = session,
                    conflictingCourse = other,
                    conflictingSession = theirSession,
                )
            }
        }

        val conflicts = conflictsById.values.toList()
        if (!force && (conflicts.isNotEmpty() || internalOverlaps.isNotEmpty())) {
            throw CourseConflictException(
                CourseConflict(
                    newCourse = course,
                    conflicts = conflicts,
                    sessionDetails = details,
                    internalOverlaps = internalOverlaps,
                ),
            )
        }

        // ===== 3. 整门课一次性写入 =====
        val stamped = course.copy(updatedAt = System.currentTimeMillis())
        repository.upsertCourseWithSessions(stamped)
        return CourseConflict(stamped, emptyList())
    }

    /**
     * 找出课时列表内部互相重叠的组合
     *
     * 两两比较（i < j），每一对只记一次。
     */
    private fun findInternalOverlaps(sessions: List<CourseSession>): List<InternalSessionOverlap> {
        if (sessions.size < 2) return emptyList()
        val result = mutableListOf<InternalSessionOverlap>()
        for (i in sessions.indices) {
            for (j in i + 1 until sessions.size) {
                if (sessions[i].overlaps(sessions[j])) {
                    result += InternalSessionOverlap(sessions[i], sessions[j])
                }
            }
        }
        return result
    }
}

/**
 * 课程冲突异常：上层捕获后可弹出"是否继续"对话框
 *
 * 消息里带上了冲突数量（跨课程门数 + 内部重叠对数）。
 */
class CourseConflictException(val conflict: CourseConflict) : Exception(
    buildString {
        append("课程与 ${conflict.conflicts.size} 门已有课程时间冲突")
        if (conflict.internalOverlaps.isNotEmpty()) {
            append("，且本课程内部有 ${conflict.internalOverlaps.size} 处课时重叠")
        }
    },
)
