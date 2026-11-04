package com.example.personalassistant.core.backup.diff

import com.example.personalassistant.core.backup.model.BackupManifest
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupPreview
import com.example.personalassistant.core.backup.model.ImportStrategy
import com.example.personalassistant.core.backup.model.TableDiff
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.feature.calendar.data.local.EventCategoryEntity
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.habit.data.local.HabitCheckInEntity
import com.example.personalassistant.feature.habit.data.local.HabitEntity
import com.example.personalassistant.feature.me.data.local.StudentProfileEntity
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskListEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 4'（plan 8f'）：备份 vs 当前数据库差异计算器（纯函数）
 *
 * 给一份 [BackupPayload] + [CurrentDbSnapshot]，产出 [BackupPreview]：
 * - 17 张表各自的 toAdd / toOverwrite 列表
 * - 默认策略 = [ImportStrategy.ADD]（最保守：只增不覆盖）
 * - 外键级联 SKIP：上游表 SKIP → 下游表自动 SKIP，写入器无法违反 FK 约束
 *
 * ## 行相等 = 主键 id 相等
 * 单 App 单设备场景，不做内容哈希。若备份行 id 在当前库中已存在 → toOverwrite；
 * 若不存在 → toAdd。
 *
 * ## `user_profiles` 特例
 * - id 一致：与普通表无异，可 ADD / OVERWRITE
 * - id 不一致：选 OVERWRITE 模式时**禁止**覆盖这条（否则 SQLite 不能改 PK，
 *   整个 user_profiles 表的更新会失败）；这里把「备份行 id 不在当前 id 集合中」的行降级为 ADD，
 *   即新建一条账户记录，id 由 Room 重新分配。后续 [BackupImporter.importWithPreview] 也按此约定。
 *
 * ## 外键拓扑
 * ```
 * user_profiles ── (accountId) ── transactions, monthly_budgets, daily_pools, categories, student_profile
 * semesters ────── (semesterId) ── courses
 * task_lists ───── (无父表，任务的归属清单)
 * tasks ─────────── (listId) ── task_lists
 * tasks ─────────── (taskId) ── task_tag_cross_ref
 * tags ──────────── (tagId)  ── task_tag_cross_ref
 * event_categories ─ (categoryId) ── events
 * categories ────── (parentId 自引用，可选)
 * habits ─────────── (habitId) ── habit_checkins
 * ```
 * 拓扑用「硬编码 adjacency list」写死，17 个节点很小，不值得跑真正的图算法。
 *
 * ⚠️ Phase 16：`events → event_categories` 是**语义依赖**（DB 层没有这条 FK，
 * `events.categoryId` 只是引用），而 DEFAULT_TABLES 里 events 排在 event_categories 之前。
 * 单遍扫描的级联推断只能看到「排在自己前面的父表」被跳过，所以这条边目前不产生级联效果 ——
 * 与 `tasks → user_profiles` 同理（都是给「用户改策略后重算级联」那一步留的数据）。
 *
 * ## 纯函数 + 无副作用
 * - 不接触 IO / DB / Clock（manifest 时间由传入的 payload 提供）
 * - 无 Hilt 依赖（构造不接任何参数 —— `@Inject` 仅为辅助 Hilt 实例化，
 *   单测里 `new BackupDiffCalculator()` 即可）
 */
@Singleton
class BackupDiffCalculator @Inject constructor() {

    /**
     * 主入口：算差异并返回预览。
     *
     * @param payload 解析后的备份内容（来自 [com.example.personalassistant.core.backup.importer.BackupImporter] 的中间产物）
     * @param manifest 备份清单（仅读 manifest 头信息；payload 已 contains 完整清单）
     * @param current 当前 DB 的 17 张表全量快照
     */
    fun compute(
        payload: BackupPayload,
        manifest: com.example.personalassistant.core.backup.model.BackupManifest,
        current: CurrentDbSnapshot,
    ): BackupPreview {
        // ===== 1. 逐表算 toAdd / toOverwrite =====
        val userProfilesDiff = diffById(
            tableName = "user_profiles",
            backup = payload.userProfiles,
            currentIds = current.userProfiles.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val semestersDiff = diffById(
            tableName = "semesters",
            backup = payload.semesters,
            currentIds = current.semesters.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val coursesDiff = diffById(
            tableName = "courses",
            backup = payload.courses,
            currentIds = current.courses.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val tagsDiff = diffById(
            tableName = "tags",
            backup = payload.tags,
            currentIds = current.tags.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val taskListsDiff = diffById(
            tableName = "task_lists",
            backup = payload.taskLists,
            currentIds = current.taskLists.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val tasksDiff = diffById(
            tableName = "tasks",
            backup = payload.tasks,
            currentIds = current.tasks.map { it.id }.toSet(),
            idOf = { it.id },
        )
        // task_tag_cross_ref 联合主键 (taskId, tagId) —— 不能直接走 id 等价
        val crossRefCurrentKeys: Set<CrossRefKey> = current.taskTagCrossRefs
            .map { CrossRefKey(it.taskId, it.tagId) }
            .toSet()
        val crossRefDiff: TableDiff<TaskTagCrossRef> = diffByCompositeKey<TaskTagCrossRef>(
            tableName = "task_tag_cross_ref",
            backup = payload.taskTagCrossRefs,
            currentKeys = crossRefCurrentKeys,
            keyOf = { CrossRefKey(it.taskId, it.tagId) },
        )
        val eventsDiff = diffById(
            tableName = "events",
            backup = payload.events,
            currentIds = current.events.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val eventCategoriesDiff = diffById(
            tableName = "event_categories",
            backup = payload.eventCategories,
            currentIds = current.eventCategories.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val categoriesDiff = diffById(
            tableName = "categories",
            backup = payload.categories,
            currentIds = current.categories.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val monthlyBudgetsDiff = diffById(
            tableName = "monthly_budgets",
            backup = payload.monthlyBudgets,
            currentIds = current.monthlyBudgets.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val dailyPoolsDiff = diffById(
            tableName = "daily_pools",
            backup = payload.dailyPools,
            currentIds = current.dailyPools.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val transactionsDiff = diffById(
            tableName = "transactions",
            backup = payload.transactions,
            currentIds = current.transactions.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val holidaysDiff = diffById(
            tableName = "holidays",
            backup = payload.holidays,
            currentIds = current.holidays.map { it.id }.toSet(),
            idOf = { it.id },
        )
        // student_profile 主键是 accountId（不是自增 id），按 accountId 判等
        val studentProfileDiff = diffById(
            tableName = "student_profile",
            backup = payload.studentProfile,
            currentIds = current.studentProfile.map { it.accountId }.toSet(),
            idOf = { it.accountId },
        )
        // Phase 23：习惯本体（habit_checkins 的父表）
        val habitsDiff = diffById(
            tableName = "habits",
            backup = payload.habits,
            currentIds = current.habits.map { it.id }.toSet(),
            idOf = { it.id },
        )
        // Phase 23：习惯打卡记录（主键是自增 id，按 id 判等；
        // `(habitId, date)` 唯一索引是业务层约束，不参与差异判定）
        val habitCheckInsDiff = diffById(
            tableName = "habit_checkins",
            backup = payload.habitCheckIns,
            currentIds = current.habitCheckIns.map { it.id }.toSet(),
            idOf = { it.id },
        )
        // 批次 C：零钱池
        //
        // ⚠️ 按 **accountId** 判等，不按 id —— `coin_pools` 的 UNIQUE 与业务主键都是 accountId，
        // `id` 只是自增列，备份导入 REPLACE 时会变。按 id 判等会让 diff 报告与实际写入对不上
        // （详见 `BackupImporter.mergeCoinPools` 的注释）。先例：`student_profile`。
        val coinPoolsDiff = diffById(
            tableName = "coin_pools",
            backup = payload.coinPools,
            currentIds = current.coinPools.map { it.accountId }.toSet(),
            idOf = { it.accountId },
        )

        val rawPerTable = listOf(
            userProfilesDiff,
            semestersDiff,
            coursesDiff,
            tagsDiff,
            taskListsDiff,
            tasksDiff,
            crossRefDiff,
            eventsDiff,
            eventCategoriesDiff,
            categoriesDiff,
            monthlyBudgetsDiff,
            dailyPoolsDiff,
            transactionsDiff,
            holidaysDiff,
            studentProfileDiff,
            habitsDiff,
            habitCheckInsDiff,
            coinPoolsDiff,
        )

        // ===== 2. 外键级联 SKIP 推断 =====
        // 按 BackupManifest.DEFAULT_TABLES 顺序计算下游表（索引 i+1..n）的拓扑，
        // 若依赖的父表策略为 SKIP，则下游表自动 SKIP（skippedByForeignKey=true）。
        // 注意：cross_ref 依赖 tasks + tags；transactions/categories 等依赖 user_profiles（隐含 accountId）。
        val perTableWithFk = applyForeignKeySkips(
            rawPerTable = rawPerTable,
            dependencies = TABLE_FK_GRAPH,
        )

        return BackupPreview(
            formatVersion = manifest.formatVersion,
            sourceDbVersion = manifest.dbVersion,
            createdAt = manifest.createdAt,
            perTable = perTableWithFk,
        )
    }

    // ---------- 单表差异：按主键 id ----------

    private fun <T : Any> diffById(
        tableName: String,
        backup: List<T>,
        currentIds: Set<Long>,
        idOf: (T) -> Long,
    ): TableDiff<T> {
        val toAdd = mutableListOf<T>()
        val toOverwrite = mutableListOf<T>()
        for (row in backup) {
            val id = idOf(row)
            if (id == 0L) {
                // id = 0L 表示「未持久化」—— 备份里不应出现，但保底走 ADD
                toAdd += row
            } else if (currentIds.contains(id)) {
                toOverwrite += row
            } else {
                toAdd += row
            }
        }
        val sampleIds = (toAdd.take(SAMPLE_LIMIT).map(idOf) +
                toOverwrite.take(SAMPLE_LIMIT).map(idOf))
            .distinct()
            .take(SAMPLE_LIMIT)
        return TableDiff(
            tableName = tableName,
            strategy = ImportStrategy.ADD,
            toAdd = toAdd,
            toOverwrite = toOverwrite,
            skippedByForeignKey = false,
            sampleRowIds = sampleIds,
        )
    }

    // ---------- 单表差异：按联合主键 (taskId, tagId) ----------

    private fun <T> diffByCompositeKey(
        tableName: String,
        backup: List<T>,
        currentKeys: Set<CrossRefKey>,
        keyOf: (T) -> CrossRefKey,
    ): TableDiff<T> {
        val toAdd = mutableListOf<T>()
        val toOverwrite = mutableListOf<T>()
        for (row in backup) {
            val key = keyOf(row)
            if (currentKeys.contains(key)) toOverwrite += row else toAdd += row
        }
        // 联合主键没有单 id，sampleRowIds 用 taskId 占位（仅展示用，无业务含义）
        val sampleIds = (toAdd.take(SAMPLE_LIMIT).map { keyOf(it).taskId } +
                toOverwrite.take(SAMPLE_LIMIT).map { keyOf(it).taskId })
            .distinct()
            .take(SAMPLE_LIMIT)
        return TableDiff(
            tableName = tableName,
            strategy = ImportStrategy.ADD,
            toAdd = toAdd,
            toOverwrite = toOverwrite,
            skippedByForeignKey = false,
            sampleRowIds = sampleIds,
        )
    }

    /**
     * 联合主键包装（避免 Tuple/Pair 装箱开销）
     */
    private data class CrossRefKey(val taskId: Long, val tagId: Long)

    // ---------- 外键级联 SKIP ----------

    /**
     * 根据外键拓扑把上游 SKIP 表的所有下游表标记为 skippedByForeignKey。
     *
     * 算法：单遍扫 rawPerTable（按 BackupManifest.DEFAULT_TABLES 顺序），
     * 维护一个 Set 记录「当前被 SKIP 的表名集合」；遇到新表时检查其依赖是否在集合中。
     *
     * @param dependencies 表名 → 它依赖的父表名集合（不含跨表 ID 含义，仅表示「写入需要父行存在」）
     */
    private fun applyForeignKeySkips(
        rawPerTable: List<TableDiff<*>>,
        dependencies: Map<String, Set<String>>,
    ): List<TableDiff<*>> {
        val skipNames = mutableSetOf<String>()
        val result = mutableListOf<TableDiff<*>>()
        for (diff in rawPerTable) {
            val deps = dependencies[diff.tableName].orEmpty()
            val fkSkipped = deps.any { it in skipNames }
            val newDiff = if (fkSkipped) {
                // 下游表被上游拖累：复制原 diff 但标 skippedByForeignKey=true，策略置 SKIP，列表清空
                TableDiff<Any>(
                    tableName = diff.tableName,
                    strategy = ImportStrategy.SKIP,
                    toAdd = emptyList(),
                    toOverwrite = emptyList(),
                    skippedByForeignKey = true,
                    sampleRowIds = diff.sampleRowIds,
                )
            } else {
                diff
            }
            result += newDiff
            if (newDiff.strategy == ImportStrategy.SKIP) {
                skipNames += newDiff.tableName
            }
        }
        return result
    }

    companion object {
        /**
         * UI 摘要用：每张表最多展示 5 行 id 样本（首屏可视范围内的代表行）
         */
        const val SAMPLE_LIMIT: Int = 5

        /**
         * 外键依赖图：表名 → 它的写入所依赖的父表名集合（隐式 FK 包括 accountId 等，不写出列名）
         *
         * 写入顺序就是 [BackupManifest.DEFAULT_TABLES]，依赖方向：**child → parents**
         * （便于扫到子表时直接查「它依赖的父是否被 SKIP」）。
         *
         * Phase 16 变化：
         * - 删除 `sub_tasks` 边（子任务表已并入 tasks，Phase 16 删表）
         * - 新增 `task_lists`（根表）、`tasks → task_lists`（tasks.listId → task_lists.id，CASCADE）
         * - 新增 `events → event_categories`（语义依赖：events.categoryId 引用分类 id）
         * - 新增 `event_categories`（根表）、`student_profile → user_profiles`（accountId）
         *
         * Phase 23 变化：
         * - 新增 `habits`（根表）、`habit_checkins → habits`（habitId → habits.id，CASCADE）
         *
         * 注意：`events → event_categories` 是**语义依赖**（DB 层没有这条 FK），
         * 而 DEFAULT_TABLES 里 events 排在 event_categories 之前，
         * 单遍扫描的级联推断覆盖不到「排在后面的父表」——详见类 KDoc 的说明。
         *
         * `habit_checkins → habits` 没有这个问题：habits 排在 habit_checkins **之前**，
         * 所以 habits 被 SKIP 时级联推断能正常把 habit_checkins 也标成 SKIP。
         */
        val TABLE_FK_GRAPH: Map<String, Set<String>> = mapOf(
            "user_profiles" to emptySet(),                       // 根表，不依赖任何表
            "semesters" to emptySet(),                           // 学期不依赖账户
            "courses" to setOf("semesters"),                     // courses.semesterId → semesters
            "tags" to emptySet(),                                // 标签不依赖其他表
            "task_lists" to emptySet(),                          // 清单表自身无父表
            "tasks" to setOf("user_profiles", "task_lists"),     // tasks.accountId + tasks.listId → task_lists.id
            "task_tag_cross_ref" to setOf("tasks", "tags"),      // (taskId, tagId)
            "events" to setOf("event_categories"),               // events.categoryId → event_categories.id
            "event_categories" to emptySet(),                    // 分类表自身无父表
            "categories" to setOf("user_profiles"),              // categories.accountId
            "monthly_budgets" to setOf("user_profiles"),         // monthly_budgets.accountId
            "daily_pools" to setOf("user_profiles"),             // daily_pools.accountId
            "transactions" to setOf("user_profiles", "categories"), // transactions.accountId + categoryId
            "holidays" to emptySet(),                            // 节假日表无 FK
            "student_profile" to setOf("user_profiles"),         // student_profile.accountId → user_profiles.id
            "habits" to emptySet(),                              // 习惯本体自身无父表
            "habit_checkins" to setOf("habits"),                 // habit_checkins.habitId → habits.id
            "coin_pools" to setOf("user_profiles"),               // 批次 C：coin_pools.accountId → user_profiles.id（真外键）
        )
    }
}