package com.example.personalassistant.feature.calendar.data.repository

import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.calendar.data.mapper.buildCoursesByDay
import com.example.personalassistant.feature.calendar.data.mapper.buildDayCourses
import com.example.personalassistant.feature.calendar.data.mapper.toDomain
import com.example.personalassistant.feature.calendar.data.mapper.toEntity
import com.example.personalassistant.feature.calendar.domain.model.CalendarHabitItem
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.DaySummary
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.MonthSummary
import com.example.personalassistant.feature.calendar.domain.repository.CalendarDisplayRepository
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import com.example.personalassistant.feature.calendar.domain.util.CalendarTaskFilter
import com.example.personalassistant.feature.calendar.domain.util.WeekCalculator
import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitCheckIn
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType
import com.example.personalassistant.feature.habit.domain.repository.HabitDisplayRepository
import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
    // Phase 20.3：日历「显示设置」（课程 / 清单项 / 已完成）
    private val calendarDisplayRepository: CalendarDisplayRepository,
    // Phase 20.3：「习惯打卡」开关复用习惯模块的（key = habit_show_in_calendar）
    private val habitDisplayRepository: HabitDisplayRepository,
    // Phase 24B：习惯数据（习惯定义 + 打卡记录）—— 日历侧只读，写走习惯域的 UseCase
    private val habitRepository: HabitRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
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

        // Phase 20.7：跨午夜的事件（23:00 → 次日 01:00）的 date 字段是第一天，
        // 它的尾巴落在第二天 —— 只按 date 查，第二天就看不到它。
        // 所以再补一路「与这一天有交集」的带时刻事件，两路去重后合并。
        val eventsFlow = combine(
            eventDao.observeByDate(date),
            eventDao.observeOverlapping(date, dayEnd),
        ) { byDate, overlapping ->
            val ids = byDate.mapTo(HashSet()) { it.id }
            (byDate + overlapping.filter { it.id !in ids }).map { it.toDomain() }
        }

        // Phase 14：
        // - 用含已完成的查询 —— 日历里勾完的任务要留着，用户手动删除才消失
        // - 区间是闭区间，end 减 1ms 等于半开 [date, 次日 00:00)，
        //   否则次日 00:00 的任务会被相邻两天同时收录（同一条任务横跨两列）
        //
        // Phase 20.2（过滤点 1/3）：**带时间段的清单项按它占用的那几天出现**，
        // 而不是按 dueDate —— 所以这里除了「截止时间落在这一天」的，
        // 还要把「时段覆盖这一天」的带上（跨午夜的会出现在两天里）。
        //
        // ⚠️ 两路可能命中同一条（既有 dueDate 又有时段），按 id 去重。
        // 排序沿用 DAO 的口径：priority DESC, dueDate ASC。
        val tasksFlow = combine(
            taskDao.observeBetweenIncludingCompleted(date, dayEnd - 1),
            taskDao.observeAll(),
        ) { dueDateTasks, allTasks ->
            // 先转成 domain —— 过滤口径（[CalendarTaskFilter]）是按 domain 模型定的
            val dueDateDomain = dueDateTasks.map { it.toDomain() }
            val allDomain = allTasks.map { it.toDomain() }
            val timed = allDomain.filter {
                CalendarTaskFilter.isTimed(it) && CalendarTaskFilter.occupiesDay(it, date)
            }
            val dueDateIds = dueDateDomain.mapTo(HashSet()) { it.id }
            (dueDateDomain + timed.filter { it.id !in dueDateIds })
                .sortedWith(
                    compareByDescending<Task> { it.priority.dbValue }
                        .thenBy { it.dueDate }
                )
        }

        val semesterFlow = semesterDao.observeCurrent()

        // courses 依赖「这一天的课时」+ weekNumber，需要 semester 数据
        //
        // Phase 28 P6：改 JOIN `course_sessions` ——
        // 之前只查 `courses.dayOfWeek`（主时段镜像），主时段在周一的课，
        // **周三的日历上看不到**（漏课）。现在按课时查，那天的课都在。
        //
        // 两路数据合并：
        // - JOIN：这一天有课时的课程（带真实课时）
        // - 镜像：没有课时记录的老课程兜底（用主时段镜像顶一个课时）
        val coursesFlow = combine(
            courseDao.observeBySessionDay(dayOfWeek),
            courseDao.observeByDayOfWeek(dayOfWeek),
            semesterFlow,
        ) { joined, mirrored, semester ->
            val weekNumber = semester?.let { WeekCalculator.weekNumber(date, it.startDate, it.endDate) }
            buildDayCourses(joined, mirrored, dayOfWeek)
                .filter { course ->
                    weekNumber == null || course.appliesToWeek(weekNumber)
                }
        }

        // Phase 24B：习惯打卡 —— 当天所有（未归档）习惯的次数
        //
        // 口径与习惯模块的 HabitDayCell 对齐：
        // - count 永远是**当天**的次数（+ / − 按钮动的就是它）
        // - isMet：每日目标看当天；每周目标看**所在周累计**（跟习惯页的月度表一致）
        //
        // 所以这里要把整周的打卡记录一起拉出来 —— 单日的 observeCheckInsOn 算不出周累计。
        val weekStart = startOfWeekMillis(date)
        val weekEnd = weekStart + 7 * ONE_DAY_MILLIS - 1
        val habitsFlow: Flow<List<CalendarHabitItem>> = combine(
            habitRepository.observeHabits(),
            habitRepository.observeCheckInsInRange(weekStart, weekEnd),
        ) { habits, checkIns ->
            buildCalendarHabitItems(habits, checkIns, date)
        }

        // Phase 20.3：显示设置（课程 / 清单项 / 已完成 / 习惯打卡）
        // 习惯开关直接复用习惯模块的 HabitDisplayRepository（key = habit_show_in_calendar），
        // 两边共用同一个 key，用户只看到一个开关。
        val displayPrefsFlow = combine(
            calendarDisplayRepository.observe(),
            habitDisplayRepository.observeShowInCalendar(),
        ) { prefs, showHabits -> prefs.copy(showHabits = showHabits) }

        // Phase 24B：学期 + 习惯 合成一路（combine 最多 5 路，先折成 1 路）
        val extrasFlow = combine(semesterFlow, habitsFlow) { semester, habits -> semester to habits }

        return combine(
            eventsFlow,
            tasksFlow,
            coursesFlow,
            extrasFlow,
            displayPrefsFlow,
        ) { events, tasks, courses, extras, prefs ->
            val (semester, habits) = extras
            val weekNumber = semester?.let { WeekCalculator.weekNumber(date, it.startDate, it.endDate) }
            DayDetail(
                date = date,
                dayOfWeek = dayOfWeek,
                events = events,
                // Phase 20.3：清单项 / 已完成 两个开关在这里生效
                tasks = prefs.filterTasks(tasks),
                courses = if (prefs.showCourses) courses else emptyList(),
                // Phase 24B：习惯打卡开关在这里生效 —— 关掉就一条都不显示
                habits = if (prefs.showHabits) habits else emptyList(),
                weekNumber = weekNumber,
            )
        }
    }

    override fun observeMonthSummary(monthStart: Long, monthEnd: Long, today: Long): Flow<MonthSummary> {
        val eventsFlow = eventDao.observeMonthRange(monthStart, monthEnd)
            .map { list -> list.map { it.toDomain() } }

        // 任务：observeBetween 用毫秒时间戳
        //
        // Phase 20.2（过滤点 2/3）：月汇总也要算上「带时间段的清单项」——
        // 它按**占用的那几天**计数（跨午夜的占两天），而不是按 dueDate。
        //
        // ⚠️ 口径统一走 [CalendarTaskFilter]（三个过滤点共用），别在这里另写一套。
        val tasksFlow = combine(
            taskDao.observeBetween(monthStart, monthEnd),
            taskDao.observeAll(),
        ) { dueDateTasks, allTasks ->
            MonthTaskBuckets(
                // 普通清单项：没有时间段的才按 dueDate 归（带时段的走下面那条路）
                plain = dueDateTasks.map { it.toDomain() }.filterNot { CalendarTaskFilter.isTimed(it) },
                // 带时段的清单项：与 observeBetween 一样只算**未完成**的
                timed = allTasks
                    .map { it.toDomain() }
                    .filter { CalendarTaskFilter.isTimed(it) && !it.isCompleted },
            )
        }

        // 课表：先按 dayOfWeek 分桶（DAO 拿所有 dayOfWeek 一次性拿）
        // 简化策略：单次拉所有 Course，客户端按 dayOfWeek + weekNumber 分组
        val coursesFlow = semesterDao.observeCurrent().map { semester ->
            semester?.let { WeekCalculator.weekNumber(today, it.startDate, it.endDate) }
        }

        // 因为 CourseDao.observeByDayOfWeek 需要 dayOfWeek 参数，
        // 单次拿所有 dayOfWeek（1..7）的课程成本太高。
        // 简化策略：直接读所有课程（observe 暂不支持）—— 这里用 monthStart..monthEnd 时间窗关联到课程当周
        // 涟宝备注：Course 表没有 date 字段，只有 dayOfWeek + weeks 字符串。
        // 为了月汇总，**改为对每个 dayOfWeek 单独观察**：
        //   - 7 次 observe + combine → 性能一般但实现简单
        //   - 后续 Phase 8 可扩展为 Room @Query 返回 List<CourseWithDayCount>

        // 先简化为：读所有 dayOfWeek 的 courses（combine 7 个 Flow）
        //
        // Phase 28 P6：改 JOIN `course_sessions` ——
        // 之前按 `courses.dayOfWeek`（主时段镜像）分桶，主时段在周一的课
        // 在周三那天不计数（漏课）。现在按课时分桶：课在哪些天有课时就占哪几天。
        val coursesByDayFlow = combine(
            courseDao.observeBySessionDays((1..7).toList()),
            courseDao.observeByDayOfWeekIn((1..7).toList()),
        ) { joined, mirrored ->
            // 性能优化：原来对 1..7 各 observe 一次（7 条独立 Room 查询），
            // 现在改成单条 `IN (1..7)` 查询 —— 查询次数 7 → 1。
            //
            // ⚠️ 关键：必须补齐 1..7 全部 key。
            // 原来 7 次 observe 每条都返回该 dow 的列表（没课就是空 list），
            // 所以 map 里一定有 7 个 key；下游会遍历每个 dow 对应的「该月所有日期」，
            // 从而给 summary 补出当月每一天的 entry（哪怕那天没课）——
            // 若这里漏掉空 dow，那些日期就不会进 summary，
            // 「今天」的 isToday 标记也会跟着丢。补齐后结果与原来逐字相同。
            buildCoursesByDay(joined, mirrored)
        }

        // Phase 24B：当月每天「有打卡记录的习惯数」
        //
        // 只算**未归档**的习惯 —— 归档的习惯不再参与日常，历史记录留着但不占月网格角标。
        val habitCountsFlow: Flow<Map<Long, Int>> = combine(
            habitRepository.observeHabits(),
            habitRepository.observeCheckInsInRange(monthStart, monthEnd - 1),
        ) { habits, checkIns ->
            val activeIds = habits.mapTo(HashSet()) { it.id }
            checkIns.asSequence()
                .filter { it.habitId in activeIds }
                .groupBy { it.date }
                .mapValues { (_, list) -> list.map { it.habitId }.distinct().size }
        }

        // Phase 20.3：显示设置（课程 / 清单项 / 已完成 / 习惯打卡）
        val displayPrefsFlow = combine(
            calendarDisplayRepository.observe(),
            habitDisplayRepository.observeShowInCalendar(),
        ) { prefs, showHabits -> prefs.copy(showHabits = showHabits) }

        // Phase 24B：周次 + 显示设置 合成一路（combine 最多 5 路）
        val extrasFlow = combine(coursesFlow, displayPrefsFlow) { weekNumber, prefs -> weekNumber to prefs }

        return combine(
            eventsFlow,
            tasksFlow,
            coursesByDayFlow,
            habitCountsFlow,
            extrasFlow,
        ) { events, tasks, coursesByDay, habitCounts, extras ->
            val (weekNumber, prefs) = extras
            val summary = mutableMapOf<Long, DaySummary>()

            // 1. events 分组
            events.groupBy { it.date }.forEach { (date, evList) ->
                val existing = summary[date] ?: DaySummary(date = date)
                summary[date] = existing.copy(eventCount = evList.size)
            }

            // 2. tasks 分组
            //
            // Phase 20.3：「清单项」开关关掉 → 这一整段不算（徽章上不显示清单项个数）
            if (prefs.showTasks) {
                // 2a. 普通清单项（task.dueDate 是毫秒时间戳，需要归到当天 00:00；null dueDate 跳过）
                tasks.plain.filter { it.dueDate != null }
                    .groupBy { startOfDayMillis(it.dueDate!!) }
                    .forEach { (date, tList) ->
                        if (date in monthStart until monthEnd) {
                            val existing = summary[date] ?: DaySummary(date = date)
                            summary[date] = existing.copy(taskCount = existing.taskCount + tList.size)
                        }
                    }
                // 2b. Phase 20.2：带时段的清单项按**占用的那几天**计数（跨午夜的占两天）
                tasks.timed.forEach { task ->
                    CalendarTaskFilter.occupiedDays(task).forEach { day ->
                        if (day in monthStart until monthEnd) {
                            val existing = summary[day] ?: DaySummary(date = day)
                            summary[day] = existing.copy(taskCount = existing.taskCount + 1)
                        }
                    }
                }
            }

            // 3. courses 按 dayOfWeek 摊到月份内每一天（依赖 weekNumber + weeks 规则）
            if (weekNumber != null) {
                coursesByDay.forEach { (dow, courseList) ->
                    // 找月份内 dayOfWeek = dow 的所有 date
                    val datesForDow = enumerateDatesForDayOfWeek(monthStart, monthEnd, dow)
                    // Phase 20.3：关掉「课程」开关 → 课程数一律记 0，
                    // 但**仍然要建出这一天的 entry**（isToday 标记挂在 entry 上）。
                    val matchingCourses = if (prefs.showCourses) {
                        courseList.filter { it.appliesToWeek(weekNumber) }
                    } else {
                        emptyList()
                    }
                    datesForDow.forEach { date ->
                        val existing = summary[date] ?: DaySummary(date = date)
                        summary[date] = existing.copy(courseCount = matchingCourses.size)
                    }
                }
            }

            // 4. 习惯打卡（Phase 24B）
            //
            // 关掉「习惯打卡」开关 → 这一整段不算（月网格上不显示习惯角标）。
            if (prefs.showHabits) {
                habitCounts.forEach { (date, count) ->
                    if (date in monthStart until monthEnd) {
                        val existing = summary[date] ?: DaySummary(date = date)
                        summary[date] = existing.copy(habitCount = count)
                    }
                }
            }

            // 5. 标记 today
            summary[today]?.let { summary[today] = it.copy(isToday = true) }

            summary
        }
            // 性能优化：上面这坨「分组 + 按 dayOfWeek 摊到每一天」是 O(n) 的重活，
            // 原来跑在收集者的上下文里（UI 侧 = 主线程）→ 月份数据一多就会掉帧。
            // flowOn 把整条上游（含 combine 的 transform）挪到 IO 线程；
            // Room 查询仍走 Room 内部 executor，产出的数据逐字相同。
            .flowOn(dispatcher)
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
     * Phase 24B：所在周的周一 00:00（毫秒时间戳）
     *
     * 周起始日是周一（与 CalendarViewModel.computeWeekRange 同一套口径）。
     */
    private fun startOfWeekMillis(date: Long): Long {
        val zone = ZoneId.systemDefault()
        val localDate = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
        val monday = localDate.minusDays((localDate.dayOfWeek.value - 1).toLong())
        return monday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * Phase 24B：把「习惯 + 一周的打卡记录」拼成日历侧的习惯条目
     *
     * - count：当天（[date]）的次数 —— + / − 按钮动的就是它
     * - isMet：每日目标看当天；每周目标看所在周累计（与习惯页月度表口径一致）
     */
    private fun buildCalendarHabitItems(
        habits: List<Habit>,
        checkIns: List<HabitCheckIn>,
        date: Long,
    ): List<CalendarHabitItem> {
        val dayCounts = HashMap<Long, Int>()
        val weekCounts = HashMap<Long, Int>()
        checkIns.forEach { checkIn ->
            weekCounts[checkIn.habitId] = (weekCounts[checkIn.habitId] ?: 0) + checkIn.count
            if (checkIn.date == date) {
                dayCounts[checkIn.habitId] = (dayCounts[checkIn.habitId] ?: 0) + checkIn.count
            }
        }
        return habits.map { habit ->
            val count = dayCounts[habit.id] ?: 0
            val met = if (habit.targetType == HabitTargetType.WEEKLY) {
                (weekCounts[habit.id] ?: 0) >= habit.targetCount
            } else {
                count >= habit.targetCount
            }
            CalendarHabitItem(
                habitId = habit.id,
                name = habit.name,
                color = habit.color,
                count = count,
                targetCount = habit.targetCount,
                isMet = met,
            )
        }
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

    // ===== Phase 8 Sub-step 3 全局搜索 =====

    override fun searchEvents(keyword: String): Flow<List<Event>> =
        eventDao.searchEvents(keyword).map { list -> list.map { it.toDomain() } }

    // ===== Phase 20.1：清单项时间段 =====

    /**
     * 给清单项设置 / 清除时间段
     *
     * - 走 TaskDao 直接改 `startAt` / `endAt` 两列（其它列原样保留）
     * - id 不存在 → 静默 no-op
     */
    override suspend fun setTaskTimeRange(taskId: Long, startAt: Long?, endAt: Long?) {
        val entity = taskDao.getById(taskId) ?: return
        taskDao.update(
            entity.copy(
                startAt = startAt,
                endAt = endAt,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}

/**
 * Phase 20.2：月汇总里的清单项分两桶
 *
 * - [plain]：没有时间段的（按 dueDate 归到某一天）
 * - [timed]：带时间段的（按占用的那几天归，跨午夜的占两天）
 */
private data class MonthTaskBuckets(
    val plain: List<Task> = emptyList(),
    val timed: List<Task> = emptyList(),
)

// ===== 跨模块 Entity → Domain 映射（轻量版，定义在这里避免污染 task/timetable） =====
// Phase 8 Sub-step 9 Step 4：CourseEntity / SemesterEntity 的 toDomain 已迁到
// com.example.personalassistant.feature.timetable.data.mapper.CourseMappers，
// 这里只保留 TaskEntity.toDomain（calendar 不依赖 task/data/mapper 以避免循环）。

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
    // Phase 20.2：时间段与金额 —— 日历要靠它们判断「这是不是一个时段块」
    amount = amount,
    startAt = startAt,
    endAt = endAt,
)