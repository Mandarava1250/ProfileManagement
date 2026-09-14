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
 * Phase 8 Sub-step 9 Step 4：**已保存**事件冲突检测
 *
 * ## "已保存"语义
 * 用于 DayDetailSheet / DayCalendarView 等视图层展示已落库的 Event。
 * 必须排除自身，避免自己和自己冲突的误报。
 *
 * ## 与 [DetectEventLiveConflictsUseCase] 的区别
 * - **本类**：excludeId = event.id（数据库内已存在的自己不算冲突）
 * - **Live**：excludeId = -1L（编辑面板的实时检测，可包含自己旧版本）
 *
 * ## 入参校验
 * event.id 必须 > 0（未保存的 event 请用 [DetectEventLiveConflictsUseCase]）。
 * 违反时抛 [IllegalArgumentException] —— 这是契约级错误，不是运行时分支。
 */
open class DetectEventSavedConflictsUseCase @Inject constructor(
    private val courseDao: CourseDao,
    private val semesterDao: SemesterDao,
) {
    open suspend operator fun invoke(event: Event): EventConflictInfo {
        require(event.id > 0L) {
            "已保存事件冲突检测要求 event.id > 0（实际 ${event.id}），请改用 DetectEventLiveConflictsUseCase"
        }

        // 1. 整天事件 → 短路
        val slice = EventTimeMapper.toSlice(event)
            ?: return EventConflictInfo(emptyList(), null, null)

        // 2. 无当前学期
        val semester = semesterDao.getCurrent()
            ?: return EventConflictInfo(emptyList(), null, slice)

        // 3. 学期外
        val weekNumber = WeekCalculator.weekNumber(
            dateMillis = event.date,
            semesterStartMillis = semester.startDate,
            semesterEndMillis = semester.endDate,
        ) ?: return EventConflictInfo(emptyList(), null, slice)

        // 4. SQL 排除自身 + 内存层周次规则
        val raw = courseDao.findConflicts(
            semester = semester.name,
            dayOfWeek = slice.dayOfWeek,
            startTime = slice.startMinutes,
            endTime = slice.endMinutes,
            excludeId = event.id,
        )
        val courses = raw.map { it.toDomain() }.filter { it.appliesToWeek(weekNumber) }
        return EventConflictInfo(courses, weekNumber, slice)
    }
}