package com.example.personalassistant.feature.calendar.data.repository

import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.calendar.data.mapper.toDomain
import com.example.personalassistant.feature.calendar.data.mapper.toEntity
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.DaySummary
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.MonthSummary
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.Semester
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7c：CalendarRepository 的 Room 实现（含 Sub-step 2 联动聚合）
 *
 * ## 职责
 * - Event 单模块 CRUD（Sub-step 1）
 * - Event + Task + Course 的联动聚合（Sub-step 2）
 *
 * ## 跨模块依赖
 * - **接受这个妥协**：calendar 模块依赖 task/timetable 模块的 DAO
 *   （plan 写明的设计 + 跟 finance/timetable 模式一致）
 * - Hilt 通过 SingletonComponent 注入所有 DAO
 *
 * @Inject + @Singleton 让 Hilt 识别为单例，
 * 由 [com.example.personalassistant.feature.calendar.di.CalendarModule] 的 @Binds 绑定到接口。
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val taskDao: TaskDao,
    private val courseDao: CourseDao,
    private val semesterDao: SemesterDao,
) : CalendarRepository {

    // ===== Event CRUD（Sub-step 1） =====

    override fun observeEventsByDate(date: Long): Flow<List<Event>> =
        eventDao.observeByDate(date).map { list -> list.map { it.toDomain() } }

    override fun observeEventsByMonthRange(monthStart: Long, monthEnd: Long): Flow<List<Event>> =
        eventDao.observeMonthRange(monthStart, monthEnd).map { list -> list.map { it.toDomain() } }

    override fun observeUpcoming(today: Long, futureEnd: Long): Flow<List<Event>> =
        eventDao.observeUpcoming(today, futureEnd).map { list -> list.map { it.toDomain() } }

    override fun observeEventById(id: Long): Flow<Event?> =
        eventDao.observeById(id).map { it?.toDomain() }

    override suspend fun getEventById(id: Long): Event? =
        eventDao.getById(id)?.toDomain()

    override suspend fun addEvent(event: Event): Long =
        eventDao.insert(event.toEntity())

    override suspend fun updateEvent(event: Event) {
        if (event.id <= 0L) return
        eventDao.update(event.toEntity())
    }

    override suspend fun deleteEvent(id: Long) {
        eventDao.deleteById(id)
    }

    override suspend fun deleteEventsBefore(date: Long) {
        eventDao.deleteBefore(date)
    }

    // ===== 联动聚合（Sub-step 2） =====

    override fun observeDayDetail(date: Long): Flow<DayDetail> {
        val dayEnd = date + ONE_DAY_MILLIS
        val dayOfWeek = dateToDayOfWeek(date)

        val eventsFlow = eventDao.observeByDate(date).map { list -> list.map { it.toDomain() } }

        // observeBetween(start, end) 已过滤 isCompleted = false + 按 priority DESC, dueDate ASC
        val tasksFlow = taskDao.observeBetween(date, dayEnd)
            .map { list -> list.map { it.toDomain() } }

        val semesterFlow = semesterDao.observeCurrent()

        // courses 依赖 dayOfWeek + weekNumber，需要 semester 数据
        val coursesFlow = combine(
            courseDao.observeByDayOfWeek(dayOfWeek),
            semesterFlow,
        ) { courses, semester ->
            val weekNumber = semester?.let { calculateWeekNumber(date, it) }
            courses
                .filter { course ->
                    weekNumber == null || course.toDomain().appliesToWeek(weekNumber)
                }
                .map { it.toDomain() }
        }

        return combine(eventsFlow, tasksFlow, coursesFlow, semesterFlow) { events, tasks, courses, semester ->
            val weekNumber = semester?.let { calculateWeekNumber(date, it) }
            DayDetail(
                date = date,
                dayOfWeek = dayOfWeek,
                events = events,
                tasks = tasks,
                courses = courses,
                weekNumber = weekNumber,
            )
        }
    }

    override fun observeMonthSummary(monthStart: Long, monthEnd: Long, today: Long): Flow<MonthSummary> {
        val eventsFlow = eventDao.observeMonthRange(monthStart, monthEnd)
            .map { list -> list.map { it.toDomain() } }

        // 任务：observeBetween 用毫秒时间戳
        val tasksFlow = taskDao.observeBetween(monthStart, monthEnd)
            .map { list -> list.map { it.toDomain() } }

        // 课表：先按 dayOfWeek 分桶（DAO 拿所有 dayOfWeek 一次性拿）
        // 简化策略：单次拉所有 Course，客户端按 dayOfWeek + weekNumber 分组
        val coursesFlow = semesterDao.observeCurrent().map { semester ->
            semester?.let { calculateWeekNumber(today, it) }
        }

        // 因为 CourseDao.observeByDayOfWeek 需要 dayOfWeek 参数，
        // 单次拿所有 dayOfWeek（1..7）的课程成本太高。
        // 简化策略：直接读所有课程（observe 暂不支持）—— 这里用 monthStart..monthEnd 时间窗关联到课程当周
        // 涟宝备注：Course 表没有 date 字段，只有 dayOfWeek + weeks 字符串。
        // 为了月汇总，**改为对每个 dayOfWeek 单独观察**：
        //   - 7 次 observe + combine → 性能一般但实现简单
        //   - 后续 Phase 8 可扩展为 Room @Query 返回 List<CourseWithDayCount>

        // 先简化为：读所有 dayOfWeek 的 courses（combine 7 个 Flow）
        val coursesByDayFlow = combine(
            (1..7).map { dow -> courseDao.observeByDayOfWeek(dow).map { list -> dow to list } },
        ) { arr ->
            val map = mutableMapOf<Int, List<CourseEntity>>()
            arr.forEach { (dow, list) -> map[dow] = list }
            map
        }

        return combine(eventsFlow, tasksFlow, coursesByDayFlow, coursesFlow) { events, tasks, coursesByDay, weekNumber ->
            val summary = mutableMapOf<Long, DaySummary>()

            // 1. events 分组
            events.groupBy { it.date }.forEach { (date, evList) ->
                val existing = summary[date] ?: DaySummary(date = date)
                summary[date] = existing.copy(eventCount = evList.size)
            }

            // 2. tasks 分组（task.dueDate 是毫秒时间戳，需要归到当天 00:00；null dueDate 跳过）
            tasks.filter { it.dueDate != null }.groupBy { startOfDayMillis(it.dueDate!!) }.forEach { (date, tList) ->
                if (date in monthStart until monthEnd) {
                    val existing = summary[date] ?: DaySummary(date = date)
                    summary[date] = existing.copy(taskCount = tList.size)
                }
            }

            // 3. courses 按 dayOfWeek 摊到月份内每一天（依赖 weekNumber + weeks 规则）
            if (weekNumber != null) {
                coursesByDay.forEach { (dow, courseList) ->
                    // 找月份内 dayOfWeek = dow 的所有 date
                    val datesForDow = enumerateDatesForDayOfWeek(monthStart, monthEnd, dow)
                    val matchingCourses = courseList.filter { it.toDomain().appliesToWeek(weekNumber) }
                    datesForDow.forEach { date ->
                        val existing = summary[date] ?: DaySummary(date = date)
                        summary[date] = existing.copy(courseCount = matchingCourses.size)
                    }
                }
            }

            // 4. 标记 today
            summary[today]?.let { summary[today] = it.copy(isToday = true) }

            summary
        }
    }

    // ===== 工具方法 =====

    /**
     * 毫秒时间戳 → ISO dayOfWeek（1=周一, 7=周日）
     */
    private fun dateToDayOfWeek(dateMillis: Long): Int {
        return LocalDate.ofEpochDay(dateMillis / ONE_DAY_MILLIS)
            .dayOfWeek.value
    }

    /**
     * 把任意时刻的毫秒时间戳归到当天 00:00
     */
    private fun startOfDayMillis(timestamp: Long): Long {
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * 计算当天在当前学期的「第几周」
     *
     * - 学期开始日视为第 1 周的第一天
     * - 无学期信息 → null
     * - 当天早于 startDate → null（学期未开始）
     */
    private fun calculateWeekNumber(dateMillis: Long, semester: SemesterEntity): Int? {
        val startMillis = semester.startDate
        if (dateMillis < startMillis) return null
        val daysDiff = (dateMillis - startMillis) / ONE_DAY_MILLIS
        return (daysDiff / 7).toInt() + 1
    }

    /**
     * 列出月范围内 dayOfWeek = dow 的所有日期（00:00 时间戳）
     */
    private fun enumerateDatesForDayOfWeek(monthStart: Long, monthEnd: Long, dow: Int): List<Long> {
        val startDate = LocalDate.ofEpochDay(monthStart / ONE_DAY_MILLIS)
        val endDate = LocalDate.ofEpochDay((monthEnd - 1) / ONE_DAY_MILLIS)
        var cursor = startDate
        // 找到第一个 dayOfWeek = dow 的日期
        while (cursor.dayOfWeek.value != dow && cursor <= endDate) {
            cursor = cursor.plusDays(1)
        }
        val dates = mutableListOf<Long>()
        while (cursor <= endDate) {
            dates.add(cursor.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
            cursor = cursor.plusDays(7)
        }
        return dates
    }

    companion object {
        const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}

// ===== 跨模块 Entity → Domain 映射（轻量版，定义在这里避免污染 task/timetable） =====

internal fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    dueDate = dueDate,
    priority = com.example.personalassistant.feature.task.domain.model.TaskPriority.fromDb(priority),
    isCompleted = isCompleted,
    category = category,
    reminderTime = reminderTime,
    createdAt = createdAt,
    updatedAt = updatedAt,
    repeatType = com.example.personalassistant.feature.task.domain.model.RepeatType.fromDb(repeatType),
    repeatEndDate = repeatEndDate,
    repeatDays = repeatDays,
    manualOrder = manualOrder,
)

internal fun CourseEntity.toDomain(): Course = Course(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    color = color,
    semester = semester,
    weeks = weeks,
    reminderMinutes = reminderMinutes,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

@Suppress("unused")
internal fun SemesterEntity.toDomain(): Semester = Semester(
    name = name,
    startDate = startDate,
    endDate = endDate,
    isCurrent = isCurrent,
    createdAt = createdAt,
)