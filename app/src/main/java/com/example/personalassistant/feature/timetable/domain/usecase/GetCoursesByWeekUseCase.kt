package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 获取某学期指定周次的所有课程
 *
 * 课程按 dayOfWeek、startTime 排序；
 * 仅返回在该周次实际开课的课程（依据 Course.appliesToWeek）。
 *
 * ## Phase 28：课程带全部课时
 *
 * 走 [TimetableRepository.observeCoursesWithSessions] —— 返回的每门课都带着自己的课时列表，
 * 下游（日历周视图）要按课时画块，只给主时段镜像会漏掉「非第一个课时」那些时段。
 */
class GetCoursesByWeekUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    operator fun invoke(semester: String, weekNumber: Int): Flow<List<Course>> {
        return kotlinx.coroutines.flow.flow {
            repository.observeCoursesWithSessions(semester).collect { all ->
                emit(
                    all.filter { it.appliesToWeek(weekNumber) }
                        .sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
                )
            }
        }
    }
}