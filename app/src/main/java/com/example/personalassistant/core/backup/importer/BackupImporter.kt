package com.example.personalassistant.core.backup.importer

import androidx.room.withTransaction
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.backup.validator.BackupValidator
import com.example.personalassistant.core.backup.validator.ValidationBundle
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.holiday.data.HolidayDao
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 4：备份导入编排（ZIP 字节 → Room 事务写入）
 *
 * 整体流程：
 * 1. [BackupValidator.validateAndExtract] 校验 ZIP（manifest + checksum）
 * 2. 解码各表 JSON 字节为 [BackupPayload]
 * 3. 在 Room 事务里：
 *    - PRAGMA foreign_keys = OFF（避免顺序问题）
 *    - 按 manifest.DEFAULT_TABLES 顺序清理每张表
 *    - 按相同顺序插入新数据
 *    - PRAGMA foreign_keys = ON
 *    - 事务失败时整库回滚（withTransaction 自动处理）
 *
 * 关键不变量：
 * - 整个写入过程是原子的（要么全成功，要么全回滚）
 * - 外键依赖顺序：semesters → tasks → sub_tasks → tags → cross_ref → events → categories → monthly_budgets → daily_pools → transactions → courses → holidays
 */
@Singleton
class BackupImporter @Inject constructor(
    private val database: AppDatabase,
    private val validator: BackupValidator,
    @com.example.personalassistant.core.backup.di.BackupJson
    private val json: Json,
) {

    /**
     * 主入口：从输入流读取 ZIP，校验后写入 Room
     */
    suspend fun import(inputStream: InputStream): BackupResult<BackupPayload> {
        val zipIn = ZipInputStream(inputStream)
        val validation = try {
            validator.validateAndExtract(zipIn)
        } catch (t: Throwable) {
            return BackupResult.Failure(
                BackupError.INVALID_ZIP,
                "ZIP 解析失败：${t.message ?: t::class.java.simpleName}",
            )
        }

        return when (validation) {
            is BackupResult.Failure -> validation
            is BackupResult.Success -> importValidatedBundle(validation.value)
        }
    }

    private suspend fun importValidatedBundle(bundle: ValidationBundle): BackupResult<BackupPayload> {
        // ===== 解码各表 JSON 字节 =====
        val payload = try {
            BackupPayload(
                semesters = decodeTable(
                    bundle.tableBytes["semesters"],
                    ListSerializer(SemesterEntity.serializer()),
                ),
                courses = decodeTable(
                    bundle.tableBytes["courses"],
                    ListSerializer(CourseEntity.serializer()),
                ),
                tags = decodeTable(
                    bundle.tableBytes["tags"],
                    ListSerializer(TagEntity.serializer()),
                ),
                tasks = decodeTable(
                    bundle.tableBytes["tasks"],
                    ListSerializer(TaskEntity.serializer()),
                ),
                subTasks = decodeTable(
                    bundle.tableBytes["sub_tasks"],
                    ListSerializer(SubTaskEntity.serializer()),
                ),
                taskTagCrossRefs = decodeTable(
                    bundle.tableBytes["task_tag_cross_ref"],
                    ListSerializer(TaskTagCrossRef.serializer()),
                ),
                events = decodeTable(
                    bundle.tableBytes["events"],
                    ListSerializer(EventEntity.serializer()),
                ),
                categories = decodeTable(
                    bundle.tableBytes["categories"],
                    ListSerializer(CategoryEntity.serializer()),
                ),
                monthlyBudgets = decodeTable(
                    bundle.tableBytes["monthly_budgets"],
                    ListSerializer(MonthlyBudgetEntity.serializer()),
                ),
                dailyPools = decodeTable(
                    bundle.tableBytes["daily_pools"],
                    ListSerializer(DailyPoolEntity.serializer()),
                ),
                transactions = decodeTable(
                    bundle.tableBytes["transactions"],
                    ListSerializer(TransactionEntity.serializer()),
                ),
                // Phase 8 Sub-step 7：节假日表
                holidays = decodeTable(
                    bundle.tableBytes["holidays"],
                    ListSerializer(HolidayEntity.serializer()),
                ),
            )
        } catch (t: Throwable) {
            return BackupResult.Failure(
                BackupError.SERIALIZATION_FAILED,
                "JSON 解析失败：${t.message ?: t::class.java.simpleName}",
            )
        }

        // ===== Room 事务批量写入 =====
        return try {
            database.withTransaction {
                // 关闭外键检查（避免插入顺序问题）
                database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")

                try {
                    val taskDao: TaskDao = database.taskDao()
                    val courseDao: CourseDao = database.courseDao()
                    val semesterDao: SemesterDao = database.semesterDao()
                    val tagDao: TagDao = database.tagDao()
                    val taskTagDao: TaskTagDao = database.taskTagDao()
                    val eventDao: EventDao = database.eventDao()
                    val financeDao: FinanceDao = database.financeDao()
                    val holidayDao: HolidayDao = database.holidayDao()

                    // ===== 清空所有表（按 manifest.DEFAULT_TABLES 反序）=====
                    clearTable { holidayDao.clearAll() }
                    clearTable { financeDao.getAllTransactions().forEach { financeDao.deleteTransactionById(it.id) } }
                    clearTable { financeDao.getAllDailyPools().forEach { financeDao.deleteDailyPoolById(it.id) } }
                    clearTable { financeDao.getAllMonthlyBudgets().forEach { financeDao.deleteMonthlyBudgetById(it.id) } }
                    clearTable { financeDao.getAllCategories().forEach { financeDao.deleteCategoryById(it.id) } }
                    clearTable { eventDao.getAllEvents().forEach { eventDao.deleteById(it.id) } }
                    clearTable { taskTagDao.getAllCrossRefs().forEach { taskTagDao.detach(it.taskId, it.tagId) } }
                    clearTable { tagDao.getAllTags().forEach { tagDao.deleteById(it.id) } }
                    clearTable { taskDao.getAllSubTasks().forEach { taskDao.deleteById(it.id) } }
                    clearTable { taskDao.getAllTasks().forEach { taskDao.deleteById(it.id) } }
                    clearTable { courseDao.getAllCourses().forEach { courseDao.deleteById(it.id) } }
                    clearTable { semesterDao.getAll().forEach { semesterDao.deleteById(it.id) } }

                    // ===== 按 manifest.DEFAULT_TABLES 顺序插入新数据 =====
                    if (payload.semesters.isNotEmpty()) {
                        payload.semesters.forEach { semesterDao.insert(it) }
                    }
                    if (payload.courses.isNotEmpty()) {
                        courseDao.insertAll(payload.courses)
                    }
                    if (payload.tags.isNotEmpty()) {
                        payload.tags.forEach { tagDao.insert(it) }
                    }
                    if (payload.tasks.isNotEmpty()) {
                        payload.tasks.forEach { taskDao.insert(it) }
                    }
                    if (payload.subTasks.isNotEmpty()) {
                        payload.subTasks.forEach { taskDao.insertSubTask(it) }
                    }
                    if (payload.taskTagCrossRefs.isNotEmpty()) {
                        payload.taskTagCrossRefs.forEach { taskTagDao.attach(it) }
                    }
                    if (payload.events.isNotEmpty()) {
                        eventDao.insertAll(payload.events)
                    }
                    if (payload.categories.isNotEmpty()) {
                        payload.categories.forEach { financeDao.insertCategory(it) }
                    }
                    if (payload.monthlyBudgets.isNotEmpty()) {
                        payload.monthlyBudgets.forEach { financeDao.setMonthlyBudget(it) }
                    }
                    if (payload.dailyPools.isNotEmpty()) {
                        payload.dailyPools.forEach { financeDao.insertDailyPool(it) }
                    }
                    if (payload.transactions.isNotEmpty()) {
                        payload.transactions.forEach { financeDao.insertTransaction(it) }
                    }
                    if (payload.holidays.isNotEmpty()) {
                        holidayDao.insertAll(payload.holidays)
                    }
                } finally {
                    database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
                }
            }
            BackupResult.Success(payload)
        } catch (t: Throwable) {
            // withTransaction 已经在内部回滚
            BackupResult.Failure(
                BackupError.IMPORT_FAILED,
                "数据库写入失败：${t.message ?: t::class.java.simpleName}",
            )
        }
    }

    private inline fun clearTable(block: () -> Unit) = block()

    private fun <T> decodeTable(
        bytes: ByteArray?,
        serializer: kotlinx.serialization.KSerializer<List<T>>,
    ): List<T> {
        if (bytes == null) return emptyList()
        return json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
    }
}
