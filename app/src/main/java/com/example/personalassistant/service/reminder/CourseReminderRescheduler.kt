package com.example.personalassistant.service.reminder

import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 课程提醒「重排」接口
 *
 * ## 为什么要有这一层
 *
 * [CourseReminderScheduler.rescheduleAll] 需要两样东西：
 * - 全部课程（带课时）
 * - 当前学期的开始日期（用来算「这是第几周」）
 *
 * 这两样都得从课表仓储里取，而 [CourseReminderScheduler] 本身是 Android 侧的具体实现
 * （AlarmManager / Context / 节假日判断），JVM 单测里造不出来。
 *
 * 所以这里抽一个**接口**：ViewModel 只认「重排一下」这个动作，
 * 具体怎么取数据、怎么排闹钟，交给 [DefaultCourseReminderRescheduler]。
 * 单测里塞一个假的进去就能验证「保存成功后确实重排了」。
 *
 * ## Phase 28 P7：接上调用方
 *
 * 在此之前 `rescheduleAll` **全项目没有调用方** —— 课程提醒的闹钟从来没排上过，
 * 通知里的课程提醒也就从来没响过。编辑页保存 / 删除课程后调用它即可重建。
 */
interface CourseReminderRescheduler {
    /** 按当前数据重建全部课程提醒闹钟 */
    suspend fun rescheduleAll()
}

/**
 * 默认实现：取当前学期的课程 + 学期开始日期，交给 [CourseReminderScheduler] 排闹钟
 *
 * ## 口径
 *
 * - 课程取**当前学期**的（`observeCoursesWithSessions(semester)`，带全部课时）
 * - 学期开始日期取当前学期的 `startDate`
 * - 没有当前学期 → `semesterStartMillis` 为 null → [CourseReminderScheduler.rescheduleAll] 直接返回
 *   （没有学期就没有「第几周」，排了也是错的）
 */
@Singleton
class DefaultCourseReminderRescheduler @Inject constructor(
    private val scheduler: CourseReminderScheduler,
    private val repository: TimetableRepository,
) : CourseReminderRescheduler {

    override suspend fun rescheduleAll() {
        val semester = repository.observeCurrentSemester().first()
        val courses = repository.observeCoursesWithSessions(semester?.name).first()
        scheduler.rescheduleAll(courses, semester?.startDate)
    }
}
