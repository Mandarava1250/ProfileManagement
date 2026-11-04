package com.example.personalassistant.core.backup.diff

import com.example.personalassistant.core.backup.model.BackupManifest
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupPreview
import com.example.personalassistant.core.backup.model.ImportStrategy
import com.example.personalassistant.core.backup.model.TableDiff
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.core.holiday.model.HolidayType
import com.example.personalassistant.feature.calendar.data.local.EventCategoryEntity
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.CoinPoolEntity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8g：补齐 8f' 欠账 —— [BackupDiffCalculator] 的 JVM 单测。
 *
 * calculator 是无参构造的纯函数（不碰 IO / DB / Clock），所以这里不需要任何 Fake，
 * 直接构造 [BackupPayload] + [CurrentDbSnapshot] 即可。
 *
 * 覆盖重点：
 * - 行归属判定（toAdd / toOverwrite / id=0 兜底）
 * - 默认策略必须是 ADD（最保守，绝不静默覆盖用户数据）
 * - 联合主键表（task_tag_cross_ref）不能走 id 等价
 * - sampleRowIds 的上限与去重
 * - 18 张表的完整性与顺序（与 [BackupManifest.DEFAULT_TABLES] 对齐）
 * - 外键拓扑表 [BackupDiffCalculator.TABLE_FK_GRAPH] 自身的一致性
 *
 * Phase 16：sub_tasks 已删表（子任务并入 tasks.parentTaskId），
 * 新增 task_lists / event_categories / student_profile 三张表。
 * Phase 23：新增 habits / habit_checkins 两张表。
 * 批次 C：新增 coin_pools 一张表（按 **accountId** 判等，不是按自增 id）。
 */
class BackupDiffCalculatorTest {

    private val calculator = BackupDiffCalculator()

    private val manifest = BackupManifest(
        application = "com.example.personalassistant",
        formatVersion = 2,
        dbVersion = 10,
        createdAt = 1_700_000_000_000L,
        tables = BackupManifest.DEFAULT_TABLES,
    )

    // ---------- fixture ----------

    private fun task(id: Long, title: String = "任务$id") =
        TaskEntity(id = id, title = title)

    private fun taskList(id: Long, name: String = "清单$id") = TaskListEntity(
        id = id,
        nameNormalized = "list$id",
        displayName = name,
    )

    private fun eventCategory(id: Long) = EventCategoryEntity(
        id = id,
        name = "分类$id",
        color = 0xFF112233.toInt(),
    )

    private fun studentProfile(accountId: Long) = StudentProfileEntity(accountId = accountId)

    private fun tag(id: Long) = TagEntity(
        id = id,
        nameNormalized = "tag$id",
        displayName = "标签$id",
        colorHex = 0xFF00FF00L,
    )

    private fun crossRef(taskId: Long, tagId: Long) =
        TaskTagCrossRef(taskId = taskId, tagId = tagId)

    private fun profile(id: Long) =
        UserProfileEntity(id = id, name = "账户$id")

    private fun semester(id: Long) = SemesterEntity(
        id = id,
        name = "学期$id",
        startDate = 1_000L,
        endDate = 2_000L,
    )

    private fun course(id: Long, semesterId: Long = 1L) = CourseEntity(
        id = id,
        name = "课程$id",
        dayOfWeek = 1,
        startTime = 480,
        endTime = 570,
        color = 0xFF112233.toInt(),
        semester = semesterId.toString(),
    )

    private fun event(id: Long) = EventEntity(id = id, title = "日程$id", date = 1_000L)

    private fun category(id: Long) =
        CategoryEntity(id = id, name = "分类$id", type = 1)

    private fun transaction(id: Long, categoryId: Long = 1L) = TransactionEntity(
        id = id,
        amount = 10.0,
        type = 1,
        categoryId = categoryId,
        date = 1_000L,
    )

    private fun monthlyBudget(id: Long) = MonthlyBudgetEntity(
        id = id,
        yearMonth = "2026-09",
        totalAmount = 1000.0,
    )

    private fun dailyPool(id: Long) = DailyPoolEntity(
        id = id,
        date = 1_000L,
        dailyBudget = 50.0,
        remainingAmount = 30.0,
    )

    private fun holiday(id: Long) = HolidayEntity(
        id = id,
        dateEpochDay = 20_000L + id,
        name = "节假日$id",
        type = HolidayType.HOLIDAY,
        year = 2026,
    )

    /** Phase 23：习惯本体 */
    private fun habit(id: Long) = HabitEntity(id = id, name = "习惯$id")

    /** Phase 23：习惯打卡记录（挂在习惯 1 上） */
    private fun habitCheckIn(id: Long, habitId: Long = 1L) = HabitCheckInEntity(
        id = id,
        habitId = habitId,
        date = 1_000L,
        count = 1,
    )

    /**
     * 批次 C：零钱池（挂在账户 1 上）
     *
     * ⚠️ `id` 与 `accountId` 是**两回事**：`id` 是自增列（备份导入 REPLACE 后会变），
     * `accountId` 才是业务主键（一账户一池，UNIQUE）。
     * 所以 fixture 把两者分开传，方便构造「id 不同但 accountId 相同」的场景。
     */
    private fun coinPool(id: Long = 1L, accountId: Long = 1L) = CoinPoolEntity(
        id = id,
        accountId = accountId,
        balance = 100.0,
    )

    /** 按表名从预览里取差异；缺失即失败（避免测试静默跳过） */
    private fun BackupPreview.diffOf(tableName: String) =
        perTable.firstOrNull { it.tableName == tableName }
            ?: error("预览缺少表 $tableName")

    private fun compute(
        payload: BackupPayload,
        current: CurrentDbSnapshot = CurrentDbSnapshot(),
    ): BackupPreview = calculator.compute(payload, manifest, current)

    // ---------- 完整性 ----------

    @Test
    fun `compute 空备份空库 产出 18 张表且全部为空`() {
        val preview = compute(BackupPayload())

        assertEquals(
            "差异结果必须覆盖 DEFAULT_TABLES 全部表",
            BackupManifest.DEFAULT_TABLES.size,
            preview.perTable.size,
        )
        assertEquals(18, preview.perTable.size)
        assertEquals(0, preview.totalToAdd)
        assertEquals(0, preview.totalToOverwrite)
        assertEquals(0, preview.totalSkippedTables)
        assertEquals(0, preview.totalSkippedByForeignKey)
    }

    @Test
    fun `表名与顺序与 DEFAULT_TABLES 完全一致`() {
        val preview = compute(BackupPayload())

        assertEquals(
            BackupManifest.DEFAULT_TABLES,
            preview.perTable.map { it.tableName },
        )
    }

    @Test
    fun `manifest 头信息透传到预览`() {
        val preview = compute(BackupPayload())

        assertEquals(manifest.formatVersion, preview.formatVersion)
        assertEquals(manifest.dbVersion, preview.sourceDbVersion)
        assertEquals(manifest.createdAt, preview.createdAt)
    }

    // ---------- 行归属判定 ----------

    @Test
    fun `备份独有的行归入 toAdd`() {
        val payload = BackupPayload(tasks = listOf(task(11), task(12)))
        val preview = compute(payload, CurrentDbSnapshot(tasks = listOf(task(99))))

        val diff = preview.diffOf("tasks")
        assertEquals(listOf(11L, 12L), diff.toAdd.map { (it as TaskEntity).id })
        assertTrue("当前库没有的行不该算覆盖", diff.toOverwrite.isEmpty())
    }

    @Test
    fun `主键 id 相同的行归入 toOverwrite`() {
        val payload = BackupPayload(tasks = listOf(task(5)))
        val preview = compute(payload, CurrentDbSnapshot(tasks = listOf(task(5))))

        val diff = preview.diffOf("tasks")
        assertTrue(diff.toAdd.isEmpty())
        assertEquals(listOf(5L), diff.toOverwrite.map { (it as TaskEntity).id })
    }

    @Test
    fun `混合场景按 id 分别归类`() {
        val payload = BackupPayload(tasks = listOf(task(1), task(2), task(3)))
        val preview = compute(payload, CurrentDbSnapshot(tasks = listOf(task(2), task(9))))

        val diff = preview.diffOf("tasks")
        assertEquals(listOf(1L, 3L), diff.toAdd.map { (it as TaskEntity).id })
        assertEquals(listOf(2L), diff.toOverwrite.map { (it as TaskEntity).id })
    }

    @Test
    fun `id 为 0 的未持久化行强制归 toAdd 即便当前库也有 0`() {
        val payload = BackupPayload(tasks = listOf(task(0L, title = "还没落库")))
        val preview = compute(payload, CurrentDbSnapshot(tasks = listOf(task(0L))))

        val diff = preview.diffOf("tasks")
        assertEquals(
            "id=0 表示未持久化，交给 Room 重新分配主键，不能当冲突",
            1,
            diff.toAdd.size,
        )
        assertTrue(diff.toOverwrite.isEmpty())
    }

    @Test
    fun `默认策略恒为 ADD`() {
        val payload = BackupPayload(
            tasks = listOf(task(1)),
            events = listOf(event(1)),
            holidays = listOf(holiday(1)),
        )
        val preview = compute(payload)

        preview.perTable.forEach { diff ->
            assertEquals(
                "表 ${diff.tableName} 的默认策略必须是 ADD",
                ImportStrategy.ADD,
                diff.strategy,
            )
            assertFalse(
                "初算阶段不应出现外键级联跳过",
                diff.skippedByForeignKey,
            )
        }
    }

    // ---------- 联合主键表 ----------

    @Test
    fun `cross_ref 按联合主键判定而非 id`() {
        val payload = BackupPayload(
            taskTagCrossRefs = listOf(crossRef(1L, 1L), crossRef(1L, 2L)),
        )
        val preview = compute(
            payload,
            CurrentDbSnapshot(taskTagCrossRefs = listOf(crossRef(1L, 1L))),
        )

        val diff = preview.diffOf("task_tag_cross_ref")
        assertEquals(
            "只有 (1,2) 是新关联",
            listOf(2L),
            diff.toAdd.map { (it as TaskTagCrossRef).tagId },
        )
        assertEquals(
            "(1,1) 已存在 → 覆盖",
            listOf(1L),
            diff.toOverwrite.map { (it as TaskTagCrossRef).tagId },
        )
    }

    @Test
    fun `cross_ref 的 sampleRowIds 用 taskId 占位`() {
        val payload = BackupPayload(
            taskTagCrossRefs = listOf(crossRef(7L, 1L), crossRef(8L, 1L)),
        )
        val preview = compute(payload)

        val diff = preview.diffOf("task_tag_cross_ref")
        assertEquals(listOf(7L, 8L), diff.sampleRowIds)
    }

    @Test
    fun `同 taskId 不同 tagId 是两行 不互相误判`() {
        val payload = BackupPayload(
            taskTagCrossRefs = listOf(crossRef(1L, 1L), crossRef(1L, 2L)),
        )
        val preview = compute(payload)

        assertEquals(2, preview.diffOf("task_tag_cross_ref").toAdd.size)
    }

    // ---------- sampleRowIds ----------

    @Test
    fun `sampleRowIds 上限为 SAMPLE_LIMIT`() {
        val payload = BackupPayload(tasks = (1L..9L).map { task(it) })
        val preview = compute(payload)

        val diff = preview.diffOf("tasks")
        assertEquals(9, diff.toAdd.size)
        assertEquals(BackupDiffCalculator.SAMPLE_LIMIT, diff.sampleRowIds.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), diff.sampleRowIds)
    }

    @Test
    fun `sampleRowIds 去重且先取新增再取覆盖`() {
        val payload = BackupPayload(tasks = listOf(task(1), task(2), task(3)))
        val preview = compute(payload, CurrentDbSnapshot(tasks = listOf(task(3))))

        val diff = preview.diffOf("tasks")
        assertEquals(listOf(1L, 2L, 3L), diff.sampleRowIds)
        assertEquals(3, diff.sampleRowIds.distinct().size)
    }

    @Test
    fun `sampleRowIds 仅为展示 不影响行数统计`() {
        val payload = BackupPayload(tasks = (1L..8L).map { task(it) })
        val preview = compute(payload)

        val diff = preview.diffOf("tasks")
        assertEquals(8, diff.toAdd.size)
        assertTrue(
            "样本数可以远小于真实行数",
            diff.sampleRowIds.size < diff.toAdd.size,
        )
        assertEquals(8, preview.totalToAdd)
    }

    // ---------- 聚合计数 ----------

    @Test
    fun `totalToAdd 与 totalToOverwrite 跨表求和`() {
        val payload = BackupPayload(
            tasks = listOf(task(1), task(2)),
            events = listOf(event(1)),
            holidays = listOf(holiday(7)),
        )
        val preview = compute(
            payload,
            CurrentDbSnapshot(
                tasks = listOf(task(2)),
                holidays = listOf(holiday(7)),
            ),
        )

        // 新增：task1 + event1 = 2；task2 与 holiday7 主键已存在 → 走覆盖
        assertEquals(2, preview.totalToAdd)
        assertEquals(2, preview.totalToOverwrite)
    }

    @Test
    fun `18 张表都能各自算出差异`() {
        val payload = BackupPayload(
            userProfiles = listOf(profile(1)),
            semesters = listOf(semester(1)),
            courses = listOf(course(1)),
            tags = listOf(tag(1)),
            taskLists = listOf(taskList(1)),
            tasks = listOf(task(1)),
            taskTagCrossRefs = listOf(crossRef(1, 1)),
            events = listOf(event(1)),
            eventCategories = listOf(eventCategory(1)),
            categories = listOf(category(1)),
            monthlyBudgets = listOf(monthlyBudget(1)),
            dailyPools = listOf(dailyPool(1)),
            transactions = listOf(transaction(1)),
            holidays = listOf(holiday(1)),
            studentProfile = listOf(studentProfile(1)),
            // Phase 23：习惯打卡两张表
            habits = listOf(habit(1)),
            habitCheckIns = listOf(habitCheckIn(1)),
            // 批次 C：零钱池
            coinPools = listOf(coinPool()),
        )
        val preview = compute(payload)

        assertEquals(18, preview.perTable.size)
        assertEquals(
            "每张表各 1 行全新数据",
            18,
            preview.totalToAdd,
        )
        preview.perTable.forEach { diff ->
            assertEquals("${diff.tableName} 应有 1 行新增", 1, diff.toAdd.size)
        }
    }

    /**
     * 批次 C：`coin_pools` 按 **accountId** 判等，不按自增 id。
     *
     * 备份里 id=5、库里 id=9，但 accountId 都是 1 —— 这是**同一个池子**（同一账户一个池），
     * 所以应判为「覆盖」而不是「新增」。
     *
     * ⚠️ 如果误按 id 判等，这里会得到 toAdd=1 / toOverwrite=0，
     * 预览界面就会告诉用户「要新增一个池子」，而实际写入时 REPLACE 会把老池子顶掉
     * —— diff 报告与实际写入对不上。
     */
    @Test
    fun `coin_pools 按 accountId 判等而非自增 id`() {
        val payload = BackupPayload(coinPools = listOf(coinPool(id = 5L, accountId = 1L)))
        val current = CurrentDbSnapshot(coinPools = listOf(coinPool(id = 9L, accountId = 1L)))

        val diff = compute(payload, current).diffOf("coin_pools")

        assertEquals("同一 accountId 应判为覆盖，而不是新增", 0, diff.toAdd.size)
        assertEquals(1, diff.toOverwrite.size)
    }

    /**
     * 批次 C：`coin_pools` 换账户 = 新增（accountId 不同就是另一个池子）。
     */
    @Test
    fun `coin_pools 换 accountId 归入 toAdd`() {
        val payload = BackupPayload(coinPools = listOf(coinPool(id = 5L, accountId = 2L)))
        val current = CurrentDbSnapshot(coinPools = listOf(coinPool(id = 9L, accountId = 1L)))

        val diff = compute(payload, current).diffOf("coin_pools")

        assertEquals(1, diff.toAdd.size)
        assertEquals(0, diff.toOverwrite.size)
    }

    // ---------- 外键拓扑表自身的一致性 ----------

    @Test
    fun `TABLE_FK_GRAPH 覆盖 DEFAULT_TABLES 每一张表`() {
        val graph = BackupDiffCalculator.TABLE_FK_GRAPH

        BackupManifest.DEFAULT_TABLES.forEach { table ->
            assertTrue(
                "拓扑图缺少表 $table 的条目，级联判断会漏掉它",
                graph.containsKey(table),
            )
        }
        assertEquals(BackupManifest.DEFAULT_TABLES.size, graph.size)
    }

    @Test
    fun `TABLE_FK_GRAPH 的依赖项都是已知表名`() {
        val known = BackupManifest.DEFAULT_TABLES.toSet()

        BackupDiffCalculator.TABLE_FK_GRAPH.forEach { (table, deps) ->
            deps.forEach { dep ->
                assertTrue(
                    "$table 依赖了未知表 $dep",
                    dep in known,
                )
            }
        }
    }

    @Test
    fun `TABLE_FK_GRAPH 不自依赖`() {
        BackupDiffCalculator.TABLE_FK_GRAPH.forEach { (table, deps) ->
            assertFalse("$table 不能依赖自己", deps.contains(table))
        }
    }

    @Test
    fun `关键依赖关系符合预期`() {
        val graph = BackupDiffCalculator.TABLE_FK_GRAPH

        assertTrue(graph.getValue("courses").contains("semesters"))
        assertTrue(graph.getValue("tasks").contains("task_lists"))
        assertTrue(graph.getValue("task_tag_cross_ref") == setOf("tasks", "tags"))
        assertTrue(graph.getValue("transactions").contains("categories"))
        assertTrue(graph.getValue("student_profile").contains("user_profiles"))

        // Phase 16：events 依赖 event_categories（语义依赖；DB 层没有这条 FK）
        assertTrue(graph.getValue("events").contains("event_categories"))

        // task_lists / event_categories 是根表；holidays 无 FK
        assertTrue(graph.getValue("task_lists").isEmpty())
        assertTrue(graph.getValue("event_categories").isEmpty())
        assertTrue(graph.getValue("holidays").isEmpty())
    }

    @Test
    fun `硬外键依赖表在写入顺序上排在被依赖表之前`() {
        val order: Map<String, Int> = BackupManifest.DEFAULT_TABLES
            .mapIndexed { index, table -> table to index }
            .toMap()
        val indexOf: (String) -> Int = { table ->
            order[table] ?: error("未知表 $table")
        }

        // ⚠️ `events → event_categories` 是**语义依赖**（DB 层没有这条 FK），
        // 而 DEFAULT_TABLES 里 events 排在 event_categories 之前 —— 这是有意为之
        // （保持既定的 15 项固定顺序），所以这里对它网开一面。
        val semanticOnlyEdges = setOf("events" to "event_categories")

        BackupDiffCalculator.TABLE_FK_GRAPH.forEach { (table, deps) ->
            deps.forEach { dep ->
                if (table to dep in semanticOnlyEdges) return@forEach
                assertTrue(
                    "$dep 必须先于 $table 写入，否则外键检查会失败",
                    indexOf(dep) < indexOf(table),
                )
            }
        }
    }

    // ---------- 级联 SKIP：现状特征测试（钉住一个已知缺陷） ----------

    /**
     * 这一组断言的是**当前真实行为**，不是理想行为。
     *
     * 已知缺陷：[BackupDiffCalculator.applyForeignKeySkips] 的级联分支在生产路径上
     * **永远不会触发** —— 它的输入 13 份 diff 全部由 `diffById` / `diffByCompositeKey`
     * 产出，而这两个函数的 `strategy` 硬编码为 [ImportStrategy.ADD]（217 行、246 行），
     * 所以扫描循环里的 `skipNames` 恒为空集，`skippedByForeignKey` 恒为 false。
     *
     * 后果：用户在预览页把 `user_profiles` 选成 SKIP 后，`tasks` / `transactions` 等
     * 下游表**不会**被联动锁定（要等 `setStrategy` 之后再算一次级联，而代码里没有这一步）。
     * 目前不炸库只是因为 `importWithPreview` 全程 `PRAGMA foreign_keys = OFF` 兜住了。
     *
     * 修复方向（留给后续 phase，见 DEV_LOG Phase 8g「不可测与缺陷清单」）：
     * 级联应改为在 `setStrategy` / `setAllStrategy` 里对**当前策略**重算，
     * 而不是在初算阶段对恒为 ADD 的输入算。
     *
     * 修好之后这组测试会变红 —— 那是预期的，届时请把断言改成新行为并同步更新本注释。
     */
    @Test
    fun `现状 任何输入组合下 compute 都不产生外键级联跳过`() {
        // 刻意构造一份「用户很可能会选 SKIP」的数据：账户表整表冲突
        val payload = BackupPayload(
            userProfiles = listOf(profile(1L), profile(2L)),
            tasks = listOf(task(1L)),
            transactions = listOf(transaction(1L)),
        )
        val current = CurrentDbSnapshot(
            userProfiles = listOf(profile(1L), profile(2L)),
            tasks = listOf(task(1L)),
            transactions = listOf(transaction(1L)),
        )

        val preview = compute(payload, current)

        assertEquals(
            "级联分支当前不可达，若此断言变红说明缺陷已修复",
            0,
            preview.totalSkippedByForeignKey,
        )
        preview.perTable.forEach { diff ->
            assertFalse("${diff.tableName} 不该被标记为外键跳过", diff.skippedByForeignKey)
        }
    }

    @Test
    fun `现状 compute 输出的策略恒为 ADD 因此级联判断没有输入`() {
        val payload = BackupPayload(
            userProfiles = listOf(profile(1L)),
            semesters = listOf(semester(1L)),
            courses = listOf(course(1L)),
            tags = listOf(tag(1L)),
            tasks = listOf(task(1L)),
            taskLists = listOf(taskList(1L)),
            events = listOf(event(1L)),
            holidays = listOf(holiday(1L)),
        )
        val preview = compute(
            payload,
            CurrentDbSnapshot(
                userProfiles = listOf(profile(1L)),
                tasks = listOf(task(1L)),
            ),
        )

        // 全部 ADD ⇒ applyForeignKeySkips 的 `strategy == SKIP` 判据永不成立
        assertTrue(
            preview.perTable.all { it.strategy == ImportStrategy.ADD },
        )
    }

    // ---------- BackupPreview 聚合属性 ----------

    @Test
    fun `totalSkippedTables 同时统计手动 SKIP 与外键锁定`() {
        val preview = BackupPreview(
            formatVersion = 2,
            sourceDbVersion = 10,
            createdAt = 1L,
            perTable = listOf(
                TableDiff(
                    tableName = "tasks",
                    strategy = ImportStrategy.SKIP,
                    toAdd = listOf(task(1L)),
                    toOverwrite = emptyList(),
                ),
                TableDiff(
                    tableName = "task_lists",
                    strategy = ImportStrategy.SKIP,
                    toAdd = emptyList<Any>(),
                    toOverwrite = emptyList(),
                    skippedByForeignKey = true,
                ),
                TableDiff(
                    tableName = "events",
                    strategy = ImportStrategy.ADD,
                    toAdd = emptyList(),
                    toOverwrite = emptyList(),
                ),
            ),
        )

        assertEquals(2, preview.totalSkippedTables)
        assertEquals(
            "只有真正被外键拖累的那张表计入 FK 统计",
            1,
            preview.totalSkippedByForeignKey,
        )
    }

    @Test
    fun `外键锁定但策略仍是 ADD 的表也被计入跳过数`() {
        // 锁定语义优先于策略显示：UI 的 chip 判断依赖这条
        val preview = BackupPreview(
            formatVersion = 2,
            sourceDbVersion = 10,
            createdAt = 1L,
            perTable = listOf(
                TableDiff(
                    tableName = "transactions",
                    strategy = ImportStrategy.ADD,
                    toAdd = listOf(transaction(1L)),
                    toOverwrite = emptyList(),
                    skippedByForeignKey = true,
                ),
            ),
        )

        assertEquals(1, preview.totalSkippedTables)
        assertEquals(1, preview.totalSkippedByForeignKey)
        assertEquals(
            "锁定不改变已算出的差异行数（仅影响是否写入）",
            1,
            preview.totalToAdd,
        )
    }
}
