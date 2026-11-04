package com.example.personalassistant.core.backup.serializer

import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.holiday.data.HolidayDao
import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.profile.data.local.UserProfileDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 4：备份序列化器（Room → 内存快照）
 *
 * 单一职责：把 Room 15 张表全量拉出，组装成 [BackupPayload]。
 * 不涉及 IO / ZIP / 偏好。导出侧的 [BackupExporter] 负责把它写到 ZIP，
 * 导入侧的 [BackupImporter] 负责把 ZIP 数据写回 Room。
 *
 * Phase 16：15 张表 = 原 13 张 - `sub_tasks` + `task_lists` + `event_categories` + `student_profile`。
 * Phase 23：17 张表 = 15 张 + `habits` + `habit_checkins`（习惯打卡）。
 *
 * 测试策略：mock 全部 DAO，验证 snapshot() 正确返回各表数据。
 */
@Singleton
class BackupSerializer @Inject constructor(
    private val courseDao: CourseDao,
    private val semesterDao: SemesterDao,
    private val taskDao: TaskDao,
    private val tagDao: TagDao,
    private val taskTagDao: TaskTagDao,
    private val eventDao: EventDao,
    private val financeDao: FinanceDao,
    private val holidayDao: HolidayDao,
    // Phase 3：账户表必须入备份，否则恢复后记账数据会指向不存在的账户
    private val userProfileDao: UserProfileDao,
    // Phase 16：清单表 / 日历事件分类 / 「我」档案三张新表
    //
    // 这三个 DAO 里只有 EventCategoryDao / StudentProfileDao 在 CoreModule 里有 Hilt provider，
    // TaskListDao 没有 —— 与其为了一个 DAO 去动 CoreModule 的绑定图（那边正在并行改造，容易撞车），
    // 不如直接拿 AppDatabase 自己取（BackupImporter / BackupCurrentSnapshotProvider 同款做法）。
    private val database: AppDatabase,
) {
    /**
     * 全量快照：从 Room 拉出 17 张表全部数据
     *
     * 全部是**跨账户全量**：备份语义 = 导出所有账户的所有数据（含账户清单本身）。
     */
    suspend fun snapshot(): BackupResult<BackupPayload> = try {
        val payload = BackupPayload(
            userProfiles = userProfileDao.getAll(),
            semesters = semesterDao.getAll(),
            courses = courseDao.getAllCourses(),
            tags = tagDao.getAllTags(),
            // Phase 16：清单表 —— tasks.listId 的父表
            taskLists = database.taskListDao().getAllTaskLists(),
            tasks = taskDao.getAllTasks(),
            taskTagCrossRefs = taskTagDao.getAllCrossRefs(),
            events = eventDao.getAllEvents(),
            // Phase 16：日历事件分类 —— events.categoryId 的父表
            eventCategories = database.eventCategoryDao().getAll(),
            categories = financeDao.getAllCategories(),
            monthlyBudgets = financeDao.getAllMonthlyBudgets(),
            dailyPools = financeDao.getAllDailyPools(),
            transactions = financeDao.getAllTransactions(),
            holidays = holidayDao.getAll(),
            // Phase 16：「我」档案 —— 一账户一行
            studentProfile = database.studentProfileDao().getAllStudentProfiles(),
            // Phase 23：习惯本体 —— habit_checkins 的父表
            habits = database.habitDao().getAll(),
            // Phase 23：习惯打卡记录
            habitCheckIns = database.habitCheckInDao().getAll(),
            // 批次 C：零钱池
            coinPools = financeDao.getAllCoinPools(),
            // 批次 D：零钱池日切结算流水
            coinPoolSettlements = financeDao.getAllCoinPoolSettlements(),
        )
        BackupResult.Success(payload)
    } catch (t: Throwable) {
        BackupResult.Failure(
            error = BackupError.SERIALIZATION_FAILED,
            message = t.message ?: "snapshot failed: ${t::class.java.simpleName}",
        )
    }
}
