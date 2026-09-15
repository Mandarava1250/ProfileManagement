package com.example.personalassistant.core.backup.importer

import androidx.room.withTransaction
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupPreview
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.backup.model.ImportStrategy
import com.example.personalassistant.core.backup.model.TableDiff
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
import com.example.personalassistant.feature.profile.data.CurrentAccountRepository
import com.example.personalassistant.feature.profile.data.local.UserProfileDao
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
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
 * - 外键依赖顺序：user_profiles → semesters → tasks → sub_tasks → tags → cross_ref → events → categories → monthly_budgets → daily_pools → transactions → courses → holidays
 *
 * ## Phase 3：账户表与「当前账户」的一致性
 * - 新备份带 user_profiles.json → 整表替换，账户清单随备份走。
 * - 旧备份（formatVersion=1）没有这张表 → **不清空不覆盖**现有账户清单；
 *   旧备份的记账 JSON 缺 accountId 字段，靠 `@Serializable` 默认值全部落到 id=1，
 *   正好对应一定存在的默认账户（迁移 / DatabaseCallback 都保证了这点）。
 * - 导入成功后要校准 DataStore 里的当前账户 id：备份可能不含当前正在用的那个账户，
 *   不校准的话切到一个不存在的账户 = 记账页永远空白。
 *
 * ## Phase 8 Sub-step 4'（plan 8f'）：合并模式
 * - 新增入口 [importWithPreview]：接收已解码的 [BackupPayload] + [BackupPreview]，
 *   按每张表的 [ImportStrategy]（ADD / OVERWRITE / SKIP）合并写入 Room。
 *   任一行失败 → 全表回滚（withTransaction 原子保证）。
 * - 旧入口 [import] 保留不变：仍走整表替换逻辑，作为「快速替换」按钮的底层路径。
 *   两者互不影响，调用方按需选择。
 */
@Singleton
open class BackupImporter @Inject constructor(
    private val database: AppDatabase,
    private val validator: BackupValidator,
    private val currentAccountRepository: CurrentAccountRepository,
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
                // Phase 3：账户表（旧备份缺这个条目 → decodeTable 返回空列表，走兼容分支）
                userProfiles = decodeTable(
                    bundle.tableBytes["user_profiles"],
                    ListSerializer(UserProfileEntity.serializer()),
                ),
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
                    val userProfileDao: UserProfileDao = database.userProfileDao()

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
                    // 账户表只在备份里确实有内容时才清空（否则会把现有账户连默认账户一起删光）
                    if (payload.userProfiles.isNotEmpty()) {
                        clearTable { userProfileDao.clearAll() }
                    }

                    // ===== 按 manifest.DEFAULT_TABLES 顺序插入新数据 =====
                    if (payload.userProfiles.isNotEmpty()) {
                        payload.userProfiles.forEach { userProfileDao.insert(it) }
                    }
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
            // 事务成功后：当前账户若已不在库里（备份里没有它），切回第一个可用账户
            reconcileCurrentAccount()
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

    /**
     * Phase 3：导入后校准「当前账户」偏好。
     *
     * DataStore 里的 currentAccountId 不在恢复出的账户清单时（典型场景：
     * 在 A 设备用账户 3 记账 → 备份 → 在 B 设备恢复，B 原本没有账户 3 之后又被覆盖），
     * 记账页会查一个不存在的账户而永远空白。这里回退到 id 最小的账户。
     *
     * 放在事务外：DataStore 不参与 Room 事务，回滚时不应误改偏好。
     */
    private suspend fun reconcileCurrentAccount() {
        val accounts = database.userProfileDao().getAll()
        if (accounts.isEmpty()) return  // 理论上不会发生（旧备份分支不清表），保底不动
        val current = currentAccountRepository.currentAccountId()
        if (accounts.none { it.id == current }) {
            currentAccountRepository.setCurrentAccountId(accounts.first().id)
        }
    }

    private fun <T> decodeTable(
        bytes: ByteArray?,
        serializer: kotlinx.serialization.KSerializer<List<T>>,
    ): List<T> {
        if (bytes == null) return emptyList()
        return json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
    }

    // ==================== Phase 8 Sub-step 4'（plan 8f'）：合并模式 ====================

    /**
     * 合并模式：按 [BackupPreview] 中每张表指定的 [ImportStrategy] 合并写入
     *
     * 与 [import] 的本质区别：
     * - [import] 走 `clearAll + insertAll` 整表替换（外键全 OFF）
     * - [importWithPreview] 走 per-table 策略（外键全 OFF，但依赖顺序仍按
     *   [BackupManifest.DEFAULT_TABLES]）
     *
     * 事务语义：**整体原子** —— 任一行写入失败 → 全部回滚。
     *
     * ## 各表策略语义
     * - [ImportStrategy.SKIP] 或 [TableDiff.skippedByForeignKey] == true → 跳过该表
     * - [ImportStrategy.ADD] → `insertAll(toAdd)`（同主键视为冲突 → 让 Room 抛 → 触发回滚）
     * - [ImportStrategy.OVERWRITE] → `insertAll(toAdd)` + `toOverwrite` 按主键 UPDATE
     *
     * ## 账户表（user_profiles）特例
     * `toOverwrite` 行若 id 与当前表撞车 → UPDATE（Room `@Update`）
     * `toAdd` 行 id = 0L 或撞车失败 → 走 INSERT（Room 自动分配新 id）
     *
     * ## reconcileCurrentAccount
     * 仅当 user_profiles 的策略非 SKIP 且实际写了至少一行才校准「当前账户」
     * （其它情况：现有账户清单没变，没必要动 DataStore）。
     */
    suspend fun importWithPreview(
        payload: BackupPayload,
        preview: BackupPreview,
    ): BackupResult<BackupPayload> {
        val strategyByTable: Map<String, TableDiff<*>> = preview.perTable.associateBy { it.tableName }
        var userProfilesTouched = false

        return try {
            database.withTransaction {
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
                    val userProfileDao: UserProfileDao = database.userProfileDao()

                    // ===== user_profiles =====
                    strategyByTable["user_profiles"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeUserProfiles(userProfileDao, diff as TableDiff<UserProfileEntity>)
                            userProfilesTouched = true
                        }
                    }

                    // ===== semesters =====
                    strategyByTable["semesters"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeSemesters(semesterDao, diff as TableDiff<SemesterEntity>)
                        }
                    }

                    // ===== courses =====
                    strategyByTable["courses"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeCourses(courseDao, diff as TableDiff<CourseEntity>)
                        }
                    }

                    // ===== tags =====
                    strategyByTable["tags"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeTags(tagDao, diff as TableDiff<TagEntity>)
                        }
                    }

                    // ===== tasks =====
                    strategyByTable["tasks"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeTasks(taskDao, diff as TableDiff<TaskEntity>)
                        }
                    }

                    // ===== sub_tasks =====
                    strategyByTable["sub_tasks"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeSubTasks(taskDao, diff as TableDiff<SubTaskEntity>)
                        }
                    }

                    // ===== task_tag_cross_ref =====
                    strategyByTable["task_tag_cross_ref"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeCrossRefs(taskTagDao, diff as TableDiff<TaskTagCrossRef>)
                        }
                    }

                    // ===== events =====
                    strategyByTable["events"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeEvents(eventDao, diff as TableDiff<EventEntity>)
                        }
                    }

                    // ===== categories =====
                    strategyByTable["categories"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeCategories(financeDao, diff as TableDiff<CategoryEntity>)
                        }
                    }

                    // ===== monthly_budgets =====
                    strategyByTable["monthly_budgets"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeMonthlyBudgets(financeDao, diff as TableDiff<MonthlyBudgetEntity>)
                        }
                    }

                    // ===== daily_pools =====
                    strategyByTable["daily_pools"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeDailyPools(financeDao, diff as TableDiff<DailyPoolEntity>)
                        }
                    }

                    // ===== transactions =====
                    strategyByTable["transactions"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeTransactions(financeDao, diff as TableDiff<TransactionEntity>)
                        }
                    }

                    // ===== holidays =====
                    strategyByTable["holidays"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeHolidays(holidayDao, diff as TableDiff<HolidayEntity>)
                        }
                    }
                } finally {
                    database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
                }
            }
            if (userProfilesTouched) reconcileCurrentAccount()
            BackupResult.Success(payload)
        } catch (t: Throwable) {
            BackupResult.Failure(
                BackupError.IMPORT_FAILED,
                "数据库写入失败：${t.message ?: t::class.java.simpleName}",
            )
        }
    }

    /**
     * 该表是否要执行写入
     * - SKIP 策略 → 否
     * - skippedByForeignKey == true（被上游拖累） → 否
     * - 其他 → 是
     */
    private fun shouldWrite(diff: TableDiff<*>): Boolean {
        if (diff.skippedByForeignKey) return false
        if (diff.strategy == ImportStrategy.SKIP) return false
        return true
    }

    // ---------- 13 张表的合并写入（per-strategy） ----------

    private suspend fun mergeUserProfiles(
        dao: UserProfileDao,
        diff: TableDiff<UserProfileEntity>,
    ) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.insert(it) }
            ImportStrategy.OVERWRITE -> {
                diff.toAdd.forEach { dao.insert(it) }
                diff.toOverwrite.forEach { existing -> dao.update(existing) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeSemesters(dao: SemesterDao, diff: TableDiff<SemesterEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.insert(it) }
            ImportStrategy.OVERWRITE -> {
                diff.toAdd.forEach { dao.insert(it) }
                diff.toOverwrite.forEach { dao.update(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeCourses(dao: CourseDao, diff: TableDiff<CourseEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertAll(diff.toAdd)
                diff.toOverwrite.forEach { dao.update(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeTags(dao: TagDao, diff: TableDiff<TagEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.insert(it) }
            ImportStrategy.OVERWRITE -> {
                // TagDao.insert 是 IGNORE 语义（同 normalized name 视为重复）；
                // OVERWRITE 需要“重建”，所以先按 id 删除旧行再插新行
                diff.toOverwrite.forEach { dao.deleteById(it.id) }
                diff.toAdd.forEach { dao.insert(it) }
                diff.toOverwrite.forEach { dao.insert(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeTasks(dao: TaskDao, diff: TableDiff<TaskEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.insert(it) }
            ImportStrategy.OVERWRITE -> {
                diff.toAdd.forEach { dao.insert(it) }
                diff.toOverwrite.forEach { dao.update(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeSubTasks(dao: TaskDao, diff: TableDiff<SubTaskEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.insertSubTask(it) }
            ImportStrategy.OVERWRITE -> {
                diff.toAdd.forEach { dao.insertSubTask(it) }
                diff.toOverwrite.forEach { dao.insertSubTask(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeCrossRefs(dao: TaskTagDao, diff: TableDiff<TaskTagCrossRef>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.attach(it) }
            ImportStrategy.OVERWRITE -> {
                (diff.toAdd + diff.toOverwrite).forEach { dao.attach(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeEvents(dao: EventDao, diff: TableDiff<EventEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertAll(diff.toAdd)
                diff.toOverwrite.forEach { dao.update(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeCategories(dao: FinanceDao, diff: TableDiff<CategoryEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.insertCategory(it) }
            ImportStrategy.OVERWRITE -> {
                diff.toAdd.forEach { dao.insertCategory(it) }
                diff.toOverwrite.forEach { dao.updateCategory(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeMonthlyBudgets(dao: FinanceDao, diff: TableDiff<MonthlyBudgetEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.setMonthlyBudget(it) }
            ImportStrategy.OVERWRITE -> {
                diff.toAdd.forEach { dao.setMonthlyBudget(it) }
                diff.toOverwrite.forEach { dao.setMonthlyBudget(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeDailyPools(dao: FinanceDao, diff: TableDiff<DailyPoolEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.insertDailyPool(it) }
            ImportStrategy.OVERWRITE -> {
                diff.toAdd.forEach { dao.insertDailyPool(it) }
                diff.toOverwrite.forEach { dao.insertDailyPool(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeTransactions(dao: FinanceDao, diff: TableDiff<TransactionEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> diff.toAdd.forEach { dao.insertTransaction(it) }
            ImportStrategy.OVERWRITE -> {
                diff.toAdd.forEach { dao.insertTransaction(it) }
                diff.toOverwrite.forEach { dao.updateTransaction(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeHolidays(dao: HolidayDao, diff: TableDiff<HolidayEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                // HolidayDao 只提供 clearAll()（节假日表全表是冷数据，覆盖语义上
                // 清空重建与「逐行删 + 插」等价）。OVERWRITE 路径：清空后重插 all。
                dao.clearAll()
                dao.insertAll(diff.toAdd + diff.toOverwrite)
            }
            ImportStrategy.SKIP -> Unit
        }
    }
}
