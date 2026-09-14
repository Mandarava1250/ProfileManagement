package com.example.personalassistant.core.backup.diff

import com.example.personalassistant.core.backup.model.BackupManifest
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupPreview
import com.example.personalassistant.core.backup.model.ImportStrategy
import com.example.personalassistant.core.backup.model.TableDiff
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 4'（plan 8f'）：备份 vs 当前数据库差异计算器（纯函数）
 *
 * 给一份 [BackupPayload] + [CurrentDbSnapshot]，产出 [BackupPreview]：
 * - 13 张表各自的 toAdd / toOverwrite 列表
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
 * user_profiles ── (accountId) ── transactions, monthly_budgets, daily_pools, categories
 * semesters ────── (semesterId) ── courses
 * tasks ─────────── (taskId) ── sub_tasks, task_tag_cross_ref
 * tags ──────────── (tagId)  ── task_tag_cross_ref
 * events ────────── (无备份 FK，备份里 events 是孤儿数据)
 * categories ────── (parentId 自引用，可选)
 * ```
 * 拓扑用「硬编码 adjacency list」写死，13 个节点很小，不值得跑真正的图算法。
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
     * @param current 当前 DB 的 13 张表全量快照
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
        val tasksDiff = diffById(
            tableName = "tasks",
            backup = payload.tasks,
            currentIds = current.tasks.map { it.id }.toSet(),
            idOf = { it.id },
        )
        val subTasksDiff = diffById(
            tableName = "sub_tasks",
            backup = payload.subTasks,
            currentIds = current.subTasks.map { it.id }.toSet(),
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

        val rawPerTable = listOf(
            userProfilesDiff,
            semestersDiff,
            coursesDiff,
            tagsDiff,
            tasksDiff,
            subTasksDiff,
            crossRefDiff,
            eventsDiff,
            categoriesDiff,
            monthlyBudgetsDiff,
            dailyPoolsDiff,
            transactionsDiff,
            holidaysDiff,
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
         * 注意：本表不含「events」，因为备份里 events 表是孤儿数据（外键指向 event_categories，
         * 而 event_categories 不在备份范围内；Phase 8 Sub-step 3 设计取舍）。
         * 即 events 不依赖任何备份表，所以可以独立 ADD/OVERWRITE/SKIP 不会级联。
         */
        val TABLE_FK_GRAPH: Map<String, Set<String>> = mapOf(
            "user_profiles" to emptySet(),                       // 根表，不依赖任何表
            "semesters" to emptySet(),                           // 学期不依赖账户
            "courses" to setOf("semesters"),                     // courses.semesterId → semesters
            "tags" to emptySet(),                                // 标签不依赖其他表
            "tasks" to setOf("user_profiles"),                   // tasks.accountId → user_profiles
            "sub_tasks" to setOf("tasks"),                       // sub_tasks.taskId → tasks
            "task_tag_cross_ref" to setOf("tasks", "tags"),      // (taskId, tagId)
            "events" to emptySet(),                              // 孤儿表（Phase 8 Sub-step 3 决策）
            "categories" to setOf("user_profiles"),              // categories.accountId
            "monthly_budgets" to setOf("user_profiles"),         // monthly_budgets.accountId
            "daily_pools" to setOf("user_profiles"),             // daily_pools.accountId
            "transactions" to setOf("user_profiles", "categories"), // transactions.accountId + categoryId
            "holidays" to emptySet(),                            // 节假日表无 FK
        )
    }
}