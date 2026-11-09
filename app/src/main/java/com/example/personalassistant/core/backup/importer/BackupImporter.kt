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
import com.example.personalassistant.feature.calendar.data.local.EventCategoryDao
import com.example.personalassistant.feature.calendar.data.local.EventCategoryEntity
import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.CoinPoolEntity
import com.example.personalassistant.feature.finance.data.local.CoinPoolSettlementEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.habit.data.local.HabitCheckInDao
import com.example.personalassistant.feature.habit.data.local.HabitCheckInEntity
import com.example.personalassistant.feature.habit.data.local.HabitDao
import com.example.personalassistant.feature.habit.data.local.HabitEntity
import com.example.personalassistant.feature.me.data.local.StudentProfileDao
import com.example.personalassistant.feature.me.data.local.StudentProfileEntity
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskListDao
import com.example.personalassistant.feature.task.data.local.TaskListEntity
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
 * - 外键依赖顺序（Phase 23 起 17 张表，与 BackupManifest.DEFAULT_TABLES 一致）：
 *   user_profiles → semesters → courses → tags → task_lists → tasks → task_tag_cross_ref
 *   → events → event_categories → categories → monthly_budgets → daily_pools → transactions
 *   → holidays → student_profile → habits → habit_checkins
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
class BackupImporter @Inject constructor(
    private val database: AppDatabase,
    private val validator: BackupValidator,
    private val currentAccountRepository: CurrentAccountRepository,
    @com.example.personalassistant.core.backup.di.BackupJson
    private val json: Json,
) : IBackupImporter {

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
                // Phase 16：清单表（旧备份缺这个条目 → decodeTable 返回空列表，走兼容分支）
                taskLists = decodeTable(
                    bundle.tableBytes["task_lists"],
                    ListSerializer(TaskListEntity.serializer()),
                ),
                tasks = decodeTable(
                    bundle.tableBytes["tasks"],
                    ListSerializer(TaskEntity.serializer()),
                ),
                taskTagCrossRefs = decodeTable(
                    bundle.tableBytes["task_tag_cross_ref"],
                    ListSerializer(TaskTagCrossRef.serializer()),
                ),
                events = decodeTable(
                    bundle.tableBytes["events"],
                    ListSerializer(EventEntity.serializer()),
                ),
                // Phase 16：日历事件分类（旧备份缺这个条目 → decodeTable 返回空列表）
                eventCategories = decodeTable(
                    bundle.tableBytes["event_categories"],
                    ListSerializer(EventCategoryEntity.serializer()),
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
                // Phase 16：「我」档案（旧备份缺这个条目 → decodeTable 返回空列表）
                studentProfile = decodeTable(
                    bundle.tableBytes["student_profile"],
                    ListSerializer(StudentProfileEntity.serializer()),
                ),
                // Phase 23：习惯本体（旧备份缺这个条目 → decodeTable 返回空列表）
                habits = decodeTable(
                    bundle.tableBytes["habits"],
                    ListSerializer(HabitEntity.serializer()),
                ),
                // Phase 23：习惯打卡记录（旧备份缺这个条目 → decodeTable 返回空列表）
                habitCheckIns = decodeTable(
                    bundle.tableBytes["habit_checkins"],
                    ListSerializer(HabitCheckInEntity.serializer()),
                ),
                // 批次 C：零钱池（旧备份缺这个条目 → decodeTable 返回空列表）
                coinPools = decodeTable(
                    bundle.tableBytes["coin_pools"],
                    ListSerializer(CoinPoolEntity.serializer()),
                ),
                // 批次 D：零钱池结算流水（旧备份缺这个条目 → decodeTable 返回空列表）
                coinPoolSettlements = decodeTable(
                    bundle.tableBytes["coin_pool_settlements"],
                    ListSerializer(CoinPoolSettlementEntity.serializer()),
                ),
            )
        } catch (t: Throwable) {
            return BackupResult.Failure(
                BackupError.SERIALIZATION_FAILED,
                "JSON 解析失败：${t.message ?: t::class.java.simpleName}",
            )
        }

        // Phase 16：三张新表（task_lists / event_categories / student_profile）在旧备份里
        // 根本没有这些条目。判据用「备份里有没有这张表」而不是「解出来有没有行」——
        // 后者分不清「备份带了但本来就是空表」（该清）与「备份根本没带」（不该清）。
        val hasTaskLists = bundle.tableBytes.containsKey("task_lists")
        val hasEventCategories = bundle.tableBytes.containsKey("event_categories")
        val hasStudentProfile = bundle.tableBytes.containsKey("student_profile")
        // Phase 23：习惯两张表同理 —— 旧备份没有这两个条目，
        // 无条件清空 = 把用户攒了很久的习惯与打卡历史删光，而备份里又没有数据可写回来。
        val hasHabits = bundle.tableBytes.containsKey("habits")
        val hasHabitCheckIns = bundle.tableBytes.containsKey("habit_checkins")
        // 批次 C：零钱池同理 —— 旧备份没有这个条目，
        // 无条件清空 = 把用户攒的零钱池余额删光，而备份里又没有数据可写回来。
        val hasCoinPools = bundle.tableBytes.containsKey("coin_pools")
        // 批次 D：结算流水同理 —— 旧备份没有这个条目，
        // 无条件清空 = 把用户攒的结算历史删光，而备份里又没有数据可写回来。
        val hasCoinPoolSettlements = bundle.tableBytes.containsKey("coin_pool_settlements")

        // ===== Room 事务批量写入 =====
        return try {
            database.withTransaction {
                // 关闭外键检查（避免插入顺序问题）
                database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")

                try {
                    val taskDao: TaskDao = database.taskDao()
                    val taskListDao: TaskListDao = database.taskListDao()
                    val courseDao: CourseDao = database.courseDao()
                    val semesterDao: SemesterDao = database.semesterDao()
                    val tagDao: TagDao = database.tagDao()
                    val taskTagDao: TaskTagDao = database.taskTagDao()
                    val eventDao: EventDao = database.eventDao()
                    val eventCategoryDao: EventCategoryDao = database.eventCategoryDao()
                    val financeDao: FinanceDao = database.financeDao()
                    val holidayDao: HolidayDao = database.holidayDao()
                    val userProfileDao: UserProfileDao = database.userProfileDao()
                    val studentProfileDao: StudentProfileDao = database.studentProfileDao()
                    // Phase 23：习惯两张表的 DAO（habit_checkins 的父表 habits 必须先清）
                    val habitDao: HabitDao = database.habitDao()
                    val habitCheckInDao: HabitCheckInDao = database.habitCheckInDao()

                    // ===== 清空所有表（按 manifest.DEFAULT_TABLES 反序）=====
                    //
                    // 每张表一次 `DELETE FROM <table>`。
                    // 原来是「先把整表读回内存，再逐行 delete」—— 10 万行流水就是
                    // 10 万次读 + 10 万次 DELETE。整表删除与「读回全表再逐行删」语义完全一致
                    // （都是把该表清空），但 IO 往返从 O(N) 降到 O(1)。
                    //
                    // Phase 23：17 张表，清空顺序 = DEFAULT_TABLES 的**反序**（先删子表、再删父表）。
                    // ⚠️ task_lists 排在 tasks 之后清 —— tasks.listId → task_lists.id 是 CASCADE，
                    //    反过来清会把清单下的任务一起带走。
                    // ⚠️ habits 排在 habit_checkins 之后清 —— habit_checkins.habitId → habits.id 是 CASCADE，
                    //    反过来清会把习惯下的打卡记录一起带走（虽然这里两张表都要清，
                    //    但顺序保持「子表先删」的一致性，将来任一张表单独清时不会踩坑）。
                    //
                    // Phase 16 的三张新表（task_lists / event_categories / student_profile）
                    // 只在**备份里确实带了这些表**时才整表替换：旧备份（formatVersion=1）
                    // 根本没有这些条目，无条件清空 = 把用户现有的清单 / 日历分类 / 档案删光，
                    // 而备份里又没有对应数据可写回来 = 纯数据丢失。
                    //
                    // Phase 23 的习惯两张表同理 —— 旧备份不带它们，就绝不能清空用户的习惯数据。
                    // 批次 C：零钱池（叶子表，先清；外键 CASCADE 也会兜底）
                    if (hasCoinPools) {
                        clearTable { financeDao.clearCoinPools() }
                    }
                    // 批次 D：零钱池结算流水（叶子表，先清；外键 CASCADE 也会兜底）
                    if (hasCoinPoolSettlements) {
                        clearTable { financeDao.clearCoinPoolSettlements() }
                    }
                    if (hasHabitCheckIns) {
                        clearTable { habitCheckInDao.clearAll() }
                    }
                    if (hasHabits) {
                        clearTable { habitDao.clearAll() }
                    }
                    if (hasStudentProfile) {
                        clearTable { studentProfileDao.clearAll() }
                    }
                    clearTable { holidayDao.clearAll() }
                    clearTable { financeDao.clearTransactions() }
                    clearTable { financeDao.clearDailyPools() }
                    clearTable { financeDao.clearMonthlyBudgets() }
                    clearTable { financeDao.clearCategories() }
                    if (hasEventCategories) {
                        clearTable { eventCategoryDao.clearAll() }
                    }
                    clearTable { eventDao.clearAll() }
                    clearTable { taskTagDao.clearCrossRefs() }
                    clearTable { taskDao.clearTasks() }
                    if (hasTaskLists) {
                        clearTable { taskListDao.clearTaskLists() }
                    }
                    clearTable { tagDao.clearTags() }
                    clearTable { courseDao.clearAll() }
                    clearTable { semesterDao.clearAll() }
                    // 账户表只在备份里确实有内容时才清空（否则会把现有账户连默认账户一起删光）
                    if (payload.userProfiles.isNotEmpty()) {
                        clearTable { userProfileDao.clearAll() }
                    }

                    // ===== 按 manifest.DEFAULT_TABLES 顺序插入新数据 =====
                    //
                    // 每张表一次批量 INSERT（Room 生成的批量接口内部逐行 bind，
                    // 冲突策略与对应的单条方法逐字一致 → 落库结果与逐行插完全相同），
                    // 但省掉了 Kotlin 侧的 N 次往返与 N 次方法调用。
                    if (payload.userProfiles.isNotEmpty()) {
                        userProfileDao.insertAll(payload.userProfiles)
                    }
                    if (payload.semesters.isNotEmpty()) {
                        semesterDao.insertAll(payload.semesters)
                    }
                    if (payload.courses.isNotEmpty()) {
                        courseDao.insertAll(payload.courses)
                    }
                    if (payload.tags.isNotEmpty()) {
                        tagDao.insertTags(payload.tags)
                    }
                    if (payload.taskLists.isNotEmpty()) {
                        taskListDao.insertTaskLists(payload.taskLists)
                    }
                    if (payload.tasks.isNotEmpty()) {
                        taskDao.insertTasks(payload.tasks)
                    }
                    if (payload.taskTagCrossRefs.isNotEmpty()) {
                        taskTagDao.attachAll(payload.taskTagCrossRefs)
                    }
                    if (payload.events.isNotEmpty()) {
                        eventDao.insertAll(payload.events)
                    }
                    if (payload.eventCategories.isNotEmpty()) {
                        eventCategoryDao.insertAll(payload.eventCategories)
                    }
                    if (payload.categories.isNotEmpty()) {
                        financeDao.insertCategories(payload.categories)
                    }
                    if (payload.monthlyBudgets.isNotEmpty()) {
                        financeDao.insertMonthlyBudgets(payload.monthlyBudgets)
                    }
                    if (payload.dailyPools.isNotEmpty()) {
                        financeDao.insertDailyPools(payload.dailyPools)
                    }
                    if (payload.transactions.isNotEmpty()) {
                        financeDao.insertTransactions(payload.transactions)
                    }
                    if (payload.holidays.isNotEmpty()) {
                        holidayDao.insertAll(payload.holidays)
                    }
                    if (payload.studentProfile.isNotEmpty()) {
                        studentProfileDao.insertAll(payload.studentProfile)
                    }
                    // Phase 23：习惯本体 —— habit_checkins 的父表，必须先于打卡记录落库
                    if (payload.habits.isNotEmpty()) {
                        habitDao.insertAll(payload.habits)
                    }
                    // Phase 23：习惯打卡记录 —— habitId 指向 habits.id，父行已就位
                    if (payload.habitCheckIns.isNotEmpty()) {
                        habitCheckInDao.insertAll(payload.habitCheckIns)
                    }
                    // 批次 C：零钱池 —— accountId → user_profiles.id，父行已就位
                    if (payload.coinPools.isNotEmpty()) {
                        financeDao.insertCoinPools(payload.coinPools)
                    }
                    // 批次 D：零钱池结算流水 —— accountId → user_profiles.id，父行已就位
                    if (payload.coinPoolSettlements.isNotEmpty()) {
                        financeDao.insertCoinPoolSettlements(payload.coinPoolSettlements)
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
    override suspend fun importWithPreview(
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
                    val taskListDao: TaskListDao = database.taskListDao()
                    val courseDao: CourseDao = database.courseDao()
                    val semesterDao: SemesterDao = database.semesterDao()
                    val tagDao: TagDao = database.tagDao()
                    val taskTagDao: TaskTagDao = database.taskTagDao()
                    val eventDao: EventDao = database.eventDao()
                    val eventCategoryDao: EventCategoryDao = database.eventCategoryDao()
                    val financeDao: FinanceDao = database.financeDao()
                    val holidayDao: HolidayDao = database.holidayDao()
                    val userProfileDao: UserProfileDao = database.userProfileDao()
                    val studentProfileDao: StudentProfileDao = database.studentProfileDao()
                    // Phase 23：习惯两张表的 DAO（habit_checkins 依赖 habits，写入顺序不能反）
                    val habitDao: HabitDao = database.habitDao()
                    val habitCheckInDao: HabitCheckInDao = database.habitCheckInDao()

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

                    // ===== task_lists =====（Phase 16：tasks.listId 的父表，必须排在 tasks 前）
                    strategyByTable["task_lists"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeTaskLists(taskListDao, diff as TableDiff<TaskListEntity>)
                        }
                    }

                    // ===== tasks =====
                    strategyByTable["tasks"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeTasks(taskDao, diff as TableDiff<TaskEntity>)
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

                    // ===== event_categories =====（Phase 16：events.categoryId 的父表）
                    strategyByTable["event_categories"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeEventCategories(eventCategoryDao, diff as TableDiff<EventCategoryEntity>)
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

                    // ===== coin_pools =====
                    strategyByTable["coin_pools"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeCoinPools(financeDao, diff as TableDiff<CoinPoolEntity>)
                        }
                    }

                    // ===== coin_pool_settlements =====
                    strategyByTable["coin_pool_settlements"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeCoinPoolSettlements(
                                financeDao,
                                diff as TableDiff<CoinPoolSettlementEntity>,
                            )
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

                    // ===== student_profile =====（Phase 16：叶子表）
                    strategyByTable["student_profile"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeStudentProfile(studentProfileDao, diff as TableDiff<StudentProfileEntity>)
                        }
                    }

                    // ===== habits =====（Phase 23：habit_checkins 的父表，必须排在打卡记录前）
                    strategyByTable["habits"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeHabits(habitDao, diff as TableDiff<HabitEntity>)
                        }
                    }

                    // ===== habit_checkins =====（Phase 23：叶子表，最后写）
                    strategyByTable["habit_checkins"]?.let { diff ->
                        if (shouldWrite(diff)) {
                            mergeHabitCheckIns(habitCheckInDao, diff as TableDiff<HabitCheckInEntity>)
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

    // ---------- 15 张表的合并写入（per-strategy） ----------

    private suspend fun mergeUserProfiles(
        dao: UserProfileDao,
        diff: TableDiff<UserProfileEntity>,
    ) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertAll(diff.toAdd)
                diff.toOverwrite.forEach { existing -> dao.update(existing) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeSemesters(dao: SemesterDao, diff: TableDiff<SemesterEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertAll(diff.toAdd)
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
            ImportStrategy.ADD -> dao.insertTags(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                // TagDao.insert 是 IGNORE 语义（同 normalized name 视为重复）；
                // OVERWRITE 需要“重建”，所以先按 id 删除旧行再插新行
                // （删除仍是逐行：标签数量很小，而批量删除要另开一个 IN 查询接口，收益不抵复杂度）
                diff.toOverwrite.forEach { dao.deleteById(it.id) }
                dao.insertTags(diff.toAdd)
                dao.insertTags(diff.toOverwrite)
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    /**
     * Phase 16：清单表合并写入
     *
     * TaskListDao.insert 是 IGNORE 语义（同 normalized name 视为重复）。
     * OVERWRITE 走 `update` 而不是「先删后插」——
     * `tasks.listId → task_lists.id` 是 CASCADE，删清单会把清单下的任务一起带走，
     * 那是「合并」不该有的副作用（用户完全可能把 tasks 表选成 SKIP）。
     */
    private suspend fun mergeTaskLists(dao: TaskListDao, diff: TableDiff<TaskListEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertTaskLists(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                diff.toOverwrite.forEach { dao.update(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeTasks(dao: TaskDao, diff: TableDiff<TaskEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertTasks(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertTasks(diff.toAdd)
                diff.toOverwrite.forEach { dao.update(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    // Phase 16：`sub_tasks` 已整表并入 `tasks`（子项改由 `tasks.parentTaskId` 自引用表达），
    // 备份链路不再有独立的 mergeSubTasks。

    private suspend fun mergeCrossRefs(dao: TaskTagDao, diff: TableDiff<TaskTagCrossRef>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.attachAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.attachAll(diff.toAdd + diff.toOverwrite)
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

    /**
     * Phase 16：日历事件分类合并写入
     *
     * EventCategoryDao.insert 是 IGNORE 语义（与单条 insert 逐字一致）；
     * OVERWRITE 用 `update` 按主键覆盖，不做「删后重插」。
     */
    private suspend fun mergeEventCategories(dao: EventCategoryDao, diff: TableDiff<EventCategoryEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                diff.toOverwrite.forEach { dao.update(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeCategories(dao: FinanceDao, diff: TableDiff<CategoryEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertCategories(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertCategories(diff.toAdd)
                diff.toOverwrite.forEach { dao.updateCategory(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeMonthlyBudgets(dao: FinanceDao, diff: TableDiff<MonthlyBudgetEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertMonthlyBudgets(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertMonthlyBudgets(diff.toAdd)
                dao.insertMonthlyBudgets(diff.toOverwrite)
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeDailyPools(dao: FinanceDao, diff: TableDiff<DailyPoolEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertDailyPools(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertDailyPools(diff.toAdd)
                dao.insertDailyPools(diff.toOverwrite)
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    /**
     * 批次 C：零钱池的合并写入
     *
     * ## 为什么按 accountId 而不是 id 判等（`BackupDiffCalculator` 那边也是这个口径）
     *
     * `coin_pools` 的 UNIQUE 是 `accountId`，业务主键也是 accountId（一账户一个池子）。
     * `id` 只是自增列，备份导入走 REPLACE 时**会变** ——
     * 用 id 判等会出现「备份里 id=5、库里 id=9，accountId 都是 1 →
     * diff 认为要新增一行 → REPLACE 撞 UNIQUE(accountId) → 把 id=9 那行顶掉」，
     * 结果碰巧是对的，但那是**碰巧**：diff 报告的「新增 1 行」与实际发生的
     * 「覆盖 1 行」对不上，预览界面会骗用户。
     *
     * 改成按 accountId 判等（照 `student_profile` 的先例）之后，
     * diff 说的「覆盖」就是真的覆盖。
     *
     * ## REPLACE 的副作用
     *
     * `insertCoinPools` 用 REPLACE：备份行的 id 与库里不同时，
     * REPLACE 会删掉冲突行（accountId 相同）并插入新行 → 结果仍是一账户一行 ✓，
     * 只是 `id` / `createdAt` 变成备份里的值 —— 这是「恢复备份」的预期语义。
     */
    private suspend fun mergeCoinPools(dao: FinanceDao, diff: TableDiff<CoinPoolEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertCoinPools(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertCoinPools(diff.toAdd)
                dao.insertCoinPools(diff.toOverwrite)
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    /**
     * 批次 D：合并零钱池结算流水
     *
     * ## 为什么按 (accountId, date) 判等
     *
     * `coin_pool_settlements` 的 UNIQUE 是 (accountId, date)，那才是业务主键；
     * `id` 只是自增列，备份导入 REPLACE 时会变。按 id 判等会让 diff 报告与实际写入对不上
     * （详见 `BackupDiffCalculator` 里同名的注释）。
     *
     * ## REPLACE 的副作用
     *
     * `insertCoinPoolSettlements` 用 REPLACE：备份行的 id 与库里不同时，
     * REPLACE 会删掉冲突行（accountId + date 相同）并插入新行
     * → 结果仍是「一账户一天一条」✓，只是 `id` / `createdAt` 变成备份里的值
     * —— 这是「恢复备份」的预期语义。
     */
    private suspend fun mergeCoinPoolSettlements(
        dao: FinanceDao,
        diff: TableDiff<CoinPoolSettlementEntity>,
    ) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertCoinPoolSettlements(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertCoinPoolSettlements(diff.toAdd)
                dao.insertCoinPoolSettlements(diff.toOverwrite)
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    private suspend fun mergeTransactions(dao: FinanceDao, diff: TableDiff<TransactionEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertTransactions(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertTransactions(diff.toAdd)
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

    /**
     * Phase 16：「我」档案合并写入
     *
     * StudentProfileDao 只提供 `upsert`（REPLACE 语义），没有 @Update 方法；
     * OVERWRITE 逐行 upsert 即可 —— REPLACE 就是「删旧行 + 插新行」，主键是 accountId。
     */
    private suspend fun mergeStudentProfile(dao: StudentProfileDao, diff: TableDiff<StudentProfileEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                diff.toOverwrite.forEach { dao.upsert(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    /**
     * Phase 23：习惯本体合并写入
     *
     * HabitDao.insertAll 是 REPLACE 语义（与单条 insert 逐字一致）；
     * OVERWRITE 用 `update` 按主键覆盖 —— 不做「删后重插」，
     * 因为 `habit_checkins.habitId → habits.id` 是 CASCADE，
     * 删习惯会把打卡记录一起带走（那是「合并」不该有的副作用）。
     */
    private suspend fun mergeHabits(dao: HabitDao, diff: TableDiff<HabitEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertAll(diff.toAdd)
                diff.toOverwrite.forEach { dao.update(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }

    /**
     * Phase 23：习惯打卡记录合并写入
     *
     * HabitCheckInDao.insertAll 是 IGNORE 语义（与单条 insert 逐字一致）：
     * `(habitId, date)` 唯一索引冲突时静默跳过 —— ADD 策略保持保守，不覆盖已有打卡。
     * OVERWRITE 走 `update` 按主键覆盖。
     */
    private suspend fun mergeHabitCheckIns(dao: HabitCheckInDao, diff: TableDiff<HabitCheckInEntity>) {
        when (diff.strategy) {
            ImportStrategy.ADD -> dao.insertAll(diff.toAdd)
            ImportStrategy.OVERWRITE -> {
                dao.insertAll(diff.toAdd)
                diff.toOverwrite.forEach { dao.update(it) }
            }
            ImportStrategy.SKIP -> Unit
        }
    }
}
