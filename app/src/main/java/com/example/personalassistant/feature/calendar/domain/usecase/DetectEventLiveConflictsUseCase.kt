package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.EventConflictInfo
import com.example.personalassistant.feature.calendar.domain.util.EventTimeMapper
import com.example.personalassistant.feature.calendar.domain.util.WeekCalculator
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.timetable.data.mapper.toDomain
import javax.inject.Inject

/**
 * Phase 8 Sub-step 9 Step 4：**实时**事件冲突检测
 *
 * ## "实时"语义
 * 用于 EditEventSheet 边输入边检测，event 可能还在编辑中（未保存）。
 *
 * ## 与 [DetectEventSavedConflictsUseCase] 的区别
 * - **本类**：excludeId = `-1L`（不排除任何课程），即便 event 自己已落库（编辑模式下 id > 0），
 *   旧版本的自己也会被检出 —— 让用户在保存前看到"原来自己跟自己冲突"的提示
 * - **Saved**：excludeId = event.id（已保存的展示视图不应与自己冲突）
 *
 * ## 返回
 * 总是返回 [EventConflictInfo]；当 event 是整天 / 无学期 / 学期外时，
 * `conflictingCourses` 为空但其他字段仍携带语义信息（供 UI 决定如何渲染）。
 */
open class DetectEventLiveConflictsUseCase @Inject constructor(
    private val courseDao: CourseDao,
    private val semesterDao: SemesterDao,
) {
    open suspend operator fun invoke(event: Event): EventConflictInfo {
        // 1. 整天事件 → 直接短路
        val slice = EventTimeMapper.toSlice(event)
            ?: return EventConflictInfo(emptyList(), null, null)

        // 2. 无当前学期 → 不参与冲突检测
        val semester = semesterDao.getCurrent()
            ?: return EventConflictInfo(emptyList(), null, slice)

        // 3. 学期外 → 不参与冲突检测
        val weekNumber = WeekCalculator.weekNumber(
            dateMillis = event.date,
            semesterStartMillis = semester.startDate,
            semesterEndMillis = semester.endDate,
        ) ?: return EventConflictInfo(emptyList(), null, slice)

        // 4. SQL 过滤（严格重叠，不接边）+ 内存层周次规则
        val raw = courseDao.findConflicts(
            semester = semester.name,
            dayOfWeek = slice.dayOfWeek,
            startTime = slice.startMinutes,
            endTime = slice.endMinutes,
            excludeId = EXCLUDE_NONE, // Live: 不排除任何课程
        )
        val courses = raw.map { it.toDomain() }.filter { it.appliesToWeek(weekNumber) }
        return EventConflictInfo(courses, weekNumber, slice)
    }

    private companion object {
        /** DAO 默认值：`-1L` 表示"不排除任何记录" */
        const val EXCLUDE_NONE: Long = -1L
    }
}