package com.example.personalassistant.core.backup.serializer

import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupResult
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
 * 单一职责：把 Room 13 张表全量拉出，组装成 [BackupPayload]。
 * 不涉及 IO / ZIP / 偏好。导出侧的 [BackupExporter] 负责把它写到 ZIP，
 * 导入侧的 [BackupImporter] 负责把 ZIP 数据写回 Room。
 *
 * 测试策略：mock 全部 8 个 DAO（task 模块 4 个 + 课程 2 个 + 财务 1 个 + 日历 1 个 + 节假日 1 个），
 * 验证 snapshot() 正确返回各表数据。
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
) {
    /**
     * 全量快照：从 Room 拉出 13 张表全部数据
     *
     * 全部是**跨账户全量**：备份语义 = 导出所有账户的所有数据（含账户清单本身）。
     */
    suspend fun snapshot(): BackupResult<BackupPayload> = try {
        val payload = BackupPayload(
            userProfiles = userProfileDao.getAll(),
            semesters = semesterDao.getAll(),
            courses = courseDao.getAllCourses(),
            tags = tagDao.getAllTags(),
            tasks = taskDao.getAllTasks(),
            subTasks = taskDao.getAllSubTasks(),
            taskTagCrossRefs = taskTagDao.getAllCrossRefs(),
            events = eventDao.getAllEvents(),
            categories = financeDao.getAllCategories(),
            monthlyBudgets = financeDao.getAllMonthlyBudgets(),
            dailyPools = financeDao.getAllDailyPools(),
            transactions = financeDao.getAllTransactions(),
            holidays = holidayDao.getAll(),
        )
        BackupResult.Success(payload)
    } catch (t: Throwable) {
        BackupResult.Failure(
            error = BackupError.SERIALIZATION_FAILED,
            message = t.message ?: "snapshot failed: ${t::class.java.simpleName}",
        )
    }
}
