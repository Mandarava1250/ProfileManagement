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
 * - 一门课的课程列表（带课时）
 * - **那个学期**的开始日期（用来算「这是第几周」）
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
 *
 * ## Phase 28 P9：按**被编辑课程所属学期**排
 *
 * 以前这个接口写死了「当前学期」：编辑「上学期」或「下学期」的课时时，
 * 重排的是**当前学期**的闹钟 —— 排出来的「第几周」是按当前学期算的，跟被编辑的课对不上。
 *
 * 现在把学期名作为参数传进来（`semesterName`）：
 * - 取该学期的课程（带课时）
 * - 取该学期的 `startDate` 算周次
 * - 只重建该学期的闹钟
 *
 * ⚠️ 口径边界：**一次只重排一个学期**。
 * 别的学期的闹钟不动（它们各自排的时候用的就是自己学期的周次，本来就是对的）。
 */
interface CourseReminderRescheduler {
    /**
     * 按当前数据重建课程提醒闹钟
     *
     * @param semesterName 要重排的**学期名**；
     *   null / 空 = 用当前学期（找不到该学期时也退回当前学期）
     */
    suspend fun rescheduleAll(semesterName: String? = null)
}

/**
 * 默认实现：取**指定学期**的课程 + 该学期的开始日期，交给 [CourseReminderScheduler] 排闹钟
 *
 * ## 口径
 *
 * - 课程取**指定学期**的（`observeCoursesWithSessions(semester.name)`，带全部课时）
 * - 学期开始日期取该学期的 `startDate` —— 「第几周」按它算
 * - 找不到该学期 → 退回当前学期
 * - 没有当前学期 → `semesterStartMillis` 为 null → [CourseReminderScheduler.rescheduleAll] 直接返回
 *   （没有学期就没有「第几周」，排了也是错的）
 */
@Singleton
class DefaultCourseReminderRescheduler @Inject constructor(
    private val scheduler: CourseReminderScheduler,
    private val repository: TimetableRepository,
) : CourseReminderRescheduler {

    override suspend fun rescheduleAll(semesterName: String?) {
        val target = semesterName?.takeIf { it.isNotBlank() }
            ?.let { repository.getSemesterByName(it) }
        val semester = target ?: repository.observeCurrentSemester().first()
        val courses = repository.observeCoursesWithSessions(semester?.name).first()
        scheduler.rescheduleAll(courses, semester?.startDate)
    }
}
