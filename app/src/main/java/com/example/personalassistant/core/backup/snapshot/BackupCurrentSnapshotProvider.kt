package com.example.personalassistant.core.backup.snapshot

import com.example.personalassistant.core.backup.diff.CurrentDbSnapshot
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.holiday.data.HolidayDao
import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.profile.data.local.UserProfileDao
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 8 Sub-step 4'（plan 8f'）：当前数据库全表快照 provider
 *
 * 一次性从所有 DAO 拉数据并打包成 [CurrentDbSnapshot] 给 [BackupDiffCalculator]。
 *
 * ## 为什么单文件 vs 在 BackupDiffCalculator 里注入 13 个 DAO
 * - 让 calculator 是**纯函数**（无 IO、无 DI）→ 易测、不需要 Robolectric
 * - 单测可以直接 `CurrentDbSnapshot(...)` 构造假数据，不依赖 Room
 * - 让 snapshot 收集保持单一职责：DAO 调用都在这里，便于审计
 *
 * ## 写入顺序对应 [BackupManifest.DEFAULT_TABLES]
 * 字段填充顺序：user_profiles → semesters → courses → tags → task_lists → tasks
 * → task_tag_cross_ref → events → event_categories → categories → monthly_budgets
 * → daily_pools → transactions → holidays → student_profile → habits → habit_checkins。
 *
 * 注意：17 张表全部一次性读，不挑顺序（与导入写入顺序无关 —— 读阶段是只读快照，
 * 计算差异是纯内存操作；写入器才是 [BackupManifest.DEFAULT_TABLES] 顺序敏感）。
 *
 * Phase 16：新增 task_lists / event_categories / student_profile 三张表；
 * 删除 sub_tasks（子任务已并入 tasks）。三个新 DAO 一律从 [AppDatabase] 取
 * （TaskListDao 没有 Hilt provider，EventCategoryDao / StudentProfileDao 也没在本类注入，
 * 统一走 database.xxxDao()，避免与 DI 图重复绑定）。
 *
 * Phase 23：新增 habits / habit_checkins 两张表，同样走 database.xxxDao()。
 */
@Singleton
open class BackupCurrentSnapshotProvider @Inject constructor(
    private val database: AppDatabase,
    private val userProfileDao: UserProfileDao,
    private val semesterDao: SemesterDao,
    private val courseDao: CourseDao,
    private val tagDao: TagDao,
    private val taskDao: TaskDao,
    private val taskTagDao: TaskTagDao,
    private val eventDao: EventDao,
    private val financeDao: FinanceDao,
    private val holidayDao: HolidayDao,
) : BackupSnapshotProvider {

    /**
     * 拉取当前数据库全部 17 张表的全量快照。
     *
     * 必须在 IO 线程调用（包含 17 次 DB 读）；调用方负责选择协程上下文。
     *
     * Phase 9 A2：[BackupSnapshotProvider] 接口契约：`suspend fun snapshot(): CurrentDbSnapshot`。
     *
     * 实现标记 `override` —— 此处原本是 `open suspend fun snapshot()`（Phase 8 T3 改的），
     * 现在变接口方法；语义不变（仍是 suspend + IO）。
     */
    override suspend fun snapshot(): CurrentDbSnapshot = withContext(Dispatchers.IO) {
        // 兜底：如果某些 DAO 注入失败或方法名变更，至少给出空 list 让计算器可工作
        // （保证 VM 不会因为单个 DAO 异常就整体崩）
        CurrentDbSnapshot(
            userProfiles = runCatching { userProfileDao.getAll() }.getOrDefault(emptyList()),
            semesters = runCatching { semesterDao.getAll() }.getOrDefault(emptyList()),
            courses = runCatching { courseDao.getAllCourses() }.getOrDefault(emptyList()),
            tags = runCatching { tagDao.getAllTags() }.getOrDefault(emptyList()),
            taskLists = runCatching { database.taskListDao().getAllTaskLists() }.getOrDefault(emptyList()),
            tasks = runCatching { taskDao.getAllTasks() }.getOrDefault(emptyList()),
            taskTagCrossRefs = runCatching { taskTagDao.getAllCrossRefs() }.getOrDefault(emptyList()),
            events = runCatching { eventDao.getAllEvents() }.getOrDefault(emptyList()),
            eventCategories = runCatching {
                database.eventCategoryDao().getAll()
            }.getOrDefault(emptyList()),
            categories = runCatching { financeDao.getAllCategories() }.getOrDefault(emptyList()),
            monthlyBudgets = runCatching { financeDao.getAllMonthlyBudgets() }.getOrDefault(emptyList()),
            dailyPools = runCatching { financeDao.getAllDailyPools() }.getOrDefault(emptyList()),
            transactions = runCatching { financeDao.getAllTransactions() }.getOrDefault(emptyList()),
            holidays = runCatching { holidayDao.getAll() }.getOrDefault(emptyList()),
            studentProfile = runCatching {
                database.studentProfileDao().getAllStudentProfiles()
            }.getOrDefault(emptyList()),
            // Phase 23：习惯本体 —— habit_checkins 的父表
            habits = runCatching { database.habitDao().getAll() }.getOrDefault(emptyList()),
            // Phase 23：习惯打卡记录
            habitCheckIns = runCatching { database.habitCheckInDao().getAll() }.getOrDefault(emptyList()),
            // 批次 C：零钱池
            coinPools = runCatching { financeDao.getAllCoinPools() }.getOrDefault(emptyList()),
        )
    }

    @Suppress("unused")
    private val dbRef: AppDatabase get() = database
}