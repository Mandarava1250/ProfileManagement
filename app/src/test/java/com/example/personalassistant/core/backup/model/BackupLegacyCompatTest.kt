package com.example.personalassistant.core.backup.model

import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.profile.data.local.AccountIds
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8g：8f' 欠账 —— **旧备份兼容性**单测。
 *
 * ## 为什么这一层值得测
 *
 * Phase 3 起记账 4 表按 `accountId` 分区。旧备份（formatVersion=1）里没有
 * `user_profiles.json`，也没有 `accountId` 字段。整套恢复逻辑吊在一个假设上：
 *
 * > 旧 JSON 缺 `accountId` → `@Serializable` 用属性默认值 → 全部落到
 * > [AccountIds.DEFAULT_ACCOUNT_ID]（= 1L）→ 而库里 id=1 的默认账户一定存在。
 *
 * 这个假设一旦破（有人改了默认值、或给字段加了改名注解却漏了默认值），
 * 后果不是崩溃而是**静默孤儿数据**：流水写进了 accountId=0，UI 按账户 1 查，
 * 用户看到记账页空空如也，数据在库里却再也删不掉。JVM 层能守住的就是这条线。
 *
 * ## 与生产代码的一致性
 *
 * [backupJson] 手工复制了 `BackupModule.provideBackupJson()` 的三个开关
 * （`ignoreUnknownKeys` / `isLenient` / `encodeDefaults`）。生产侧若改配置，
 * 这里要同步 —— 否则测的是另一个 Json 的行为。
 *
 * ## 写 JSON fixture 的注意
 *
 * 一律用**普通双引号字符串 + 转义**，不用三引号模板：JSON 内容本身全是双引号，
 * 混用时结尾引号数量极易数错（本项目 Phase 8g 踩过一次，编译报 Unexpected tokens）。
 * 另外 JSON 没有 Long 字面量，`1L` 是非法 token，会抛 JsonDecodingException。
 *
 * ## 覆盖不到的部分
 *
 * [com.example.personalassistant.core.backup.importer.BackupImporter] 的
 * `importWithPreview` / `mergeXxx` 是 `private suspend` 且直接依赖 Room `AppDatabase`，
 * 纯 JVM 无法实例化（项目未引入 Robolectric / mock 框架，
 * `isIncludeAndroidResources = false`）。因此「DAO 真的按兼容结果写进了库」
 * 这一段属于 androidTest 范畴，见 DEV_LOG Phase 8g「不可测清单」。
 */
class BackupLegacyCompatTest {

    private val backupJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * `ListSerializer(...)` 是**函数**（返回 `KSerializer<List<T>>`），不是类型；
     * 所以形参必须写 [KSerializer]，与生产侧
     * [com.example.personalassistant.feature.settings.backup.BackupPreviewViewModel]
     * 的 `decodePayload` 内部 helper 保持同一写法。
     */
    private fun <T> decodeList(
        raw: String,
        serializer: KSerializer<List<T>>,
    ): List<T> = backupJson.decodeFromString(serializer, raw)

    private fun decodeTasks(raw: String) =
        decodeList(raw, ListSerializer(TaskEntity.serializer()))

    private fun decodeTransactions(raw: String) =
        decodeList(raw, ListSerializer(TransactionEntity.serializer()))

    // ---------- 缺 accountId 的旧 JSON ----------

    @Test
    fun `旧 transactions JSON 无 accountId 字段可正常解码`() {
        val legacy = "[{\"id\":11,\"amount\":25.5,\"type\":0,\"categoryId\":3,\"date\":1700000000000}]"

        val rows = decodeTransactions(legacy)

        assertEquals(1, rows.size)
        assertEquals(11L, rows[0].id)
        assertEquals(25.5, rows[0].amount, 0.0001)
        assertEquals(3L, rows[0].categoryId)
    }

    @Test
    fun `旧 JSON 缺 accountId 时全部落到默认账户`() {
        val legacy = "[" +
                "{\"id\":1,\"amount\":10.0,\"type\":0,\"categoryId\":1,\"date\":1}," +
                "{\"id\":2,\"amount\":20.0,\"type\":1,\"categoryId\":2,\"date\":2}" +
                "]"

        val rows = decodeTransactions(legacy)

        assertEquals(2, rows.size)
        rows.forEach { row ->
            assertEquals(
                "流水 id=" + row.id + " 的 accountId 必须是默认账户，否则恢复后成为孤儿数据",
                AccountIds.DEFAULT_ACCOUNT_ID,
                row.accountId,
            )
        }
    }

    @Test
    fun `默认账户 id 恒为 1L`() {
        // 该常量与 DatabaseCallback / 迁移里预置的默认账户绑定，改动必须同步迁移
        assertEquals(1L, AccountIds.DEFAULT_ACCOUNT_ID)
    }

    @Test
    fun `记账四张表缺失 accountId 时都回落到默认账户`() {
        val legacyTx = decodeTransactions(
            "[{\"id\":1,\"amount\":1.0,\"type\":0,\"categoryId\":1,\"date\":1}]",
        )
        val legacyCat = decodeList(
            "[{\"id\":2,\"name\":\"餐饮\",\"type\":0}]",
            ListSerializer(CategoryEntity.serializer()),
        )
        val legacyBudget = decodeList(
            "[{\"id\":3,\"yearMonth\":\"2026-09\",\"totalAmount\":100.0}]",
            ListSerializer(MonthlyBudgetEntity.serializer()),
        )
        val legacyPool = decodeList(
            "[{\"id\":4,\"date\":1,\"dailyBudget\":10.0,\"remainingAmount\":5.0}]",
            ListSerializer(DailyPoolEntity.serializer()),
        )

        assertEquals(AccountIds.DEFAULT_ACCOUNT_ID, legacyTx.single().accountId)
        assertEquals(AccountIds.DEFAULT_ACCOUNT_ID, legacyCat.single().accountId)
        assertEquals(AccountIds.DEFAULT_ACCOUNT_ID, legacyBudget.single().accountId)
        assertEquals(AccountIds.DEFAULT_ACCOUNT_ID, legacyPool.single().accountId)
    }

    @Test
    fun `新备份显式带 accountId 时不被默认值覆盖`() {
        val modern = "[{\"id\":1,\"accountId\":7,\"amount\":1.0,\"type\":0,\"categoryId\":1,\"date\":1}]"

        val row = decodeTransactions(modern).single()

        assertEquals("显式值必须优先于默认值", 7L, row.accountId)
    }

    // ---------- 缺 user_profiles 条目 ----------

    @Test
    fun `旧备份没有 user_profiles 时 payload 解码为空列表`() {
        // formatVersion=1 的 ZIP 里根本没有 user_profiles.json，
        // 解码端把缺失条目当空列表处理（见 BackupPreviewViewModel.decodePayload）
        val decoded = backupJson.decodeFromString(
            BackupPayload.serializer(),
            "{\"tasks\":[{\"id\":1,\"title\":\"旧任务\"}]}",
        )

        assertTrue(
            "旧备份不应凭空解出账户",
            decoded.userProfiles.isEmpty(),
        )
        assertEquals(1, decoded.tasks.size)
        assertEquals(1, decoded.totalRecordCount)
    }

    @Test
    fun `user_profiles 空与非空可区分 导入侧据此决定是否清表`() {
        // BackupImporter 用 isNotEmpty() 决定是否 clearAll，这里钉住这个判据本身
        val fromLegacy = BackupPayload(userProfiles = emptyList())
        val fromModern = BackupPayload(
            userProfiles = listOf(UserProfileEntity(id = 1L, name = "伙伴")),
        )

        assertTrue(fromLegacy.userProfiles.isEmpty())
        assertTrue(fromModern.userProfiles.isNotEmpty())
    }

    // ---------- 未知字段与缺失字段 ----------

    @Test
    fun `未知字段被忽略 便于向后兼容`() {
        val future = "[{\"id\":1,\"title\":\"带新字段\",\"someFutureColumn\":123,\"nested\":{\"a\":1}}]"

        val row = decodeTasks(future).single()

        assertEquals(1L, row.id)
        assertEquals("带新字段", row.title)
    }

    @Test
    fun `缺失可选字段用属性默认值补齐`() {
        val minimal = "[{\"id\":1,\"title\":\"最省字段\"}]"

        val row = decodeTasks(minimal).single()

        assertTrue("未写 dueDate 视为无截止日", row.dueDate == null)
        assertTrue("未写 isCompleted 默认未完成", !row.isCompleted)
    }

    @Test
    fun `v6 之前无 holidays 字段时解出空列表`() {
        val preV6 = "{\"tasks\":[],\"events\":[]}"

        val payload = backupJson.decodeFromString(BackupPayload.serializer(), preV6)

        assertTrue(payload.holidays.isEmpty())
        assertEquals(0, payload.totalRecordCount)
    }

    @Test
    fun `空数组 JSON 解出空列表而非 null`() {
        // exporter 对空表写 "[]"，导入端必须解成 emptyList，不能让 null 漏进 payload
        val rows = decodeList("[]", ListSerializer(EventEntity.serializer()))

        assertNotNull(rows)
        assertTrue(rows.isEmpty())
    }

    // ---------- 表清单不变量 ----------

    @Test
    fun `DEFAULT_TABLES 为 15 张且顺序与内容固定`() {
        assertEquals(15, BackupManifest.DEFAULT_TABLES.size)
        assertEquals("user_profiles", BackupManifest.DEFAULT_TABLES.first())
        assertEquals("student_profile", BackupManifest.DEFAULT_TABLES.last())
        assertEquals(
            "表清单去重后仍应是 15 项",
            15,
            BackupManifest.DEFAULT_TABLES.distinct().size,
        )
    }

    @Test
    fun `manifest 带全部 15 张表可 round-trip`() {
        val manifest = BackupManifest(
            application = "com.example.personalassistant",
            formatVersion = BackupFormatVersion.FORMAT_VERSION,
            dbVersion = BackupFormatVersion.DB_VERSION,
            createdAt = 1_700_000_000_000L,
            tables = BackupManifest.DEFAULT_TABLES,
        )

        val text = backupJson.encodeToString(BackupManifest.serializer(), manifest)
        val decoded = backupJson.decodeFromString(BackupManifest.serializer(), text)

        assertEquals(manifest, decoded)
    }

    @Test
    fun `payload 完整 round-trip 保持各表行数`() {
        val payload = BackupPayload(
            userProfiles = listOf(UserProfileEntity(id = 1L, name = "伙伴")),
            tags = listOf(
                TagEntity(
                    id = 1L,
                    nameNormalized = "study",
                    displayName = "学习",
                    colorHex = 0xFF00FF00L,
                ),
            ),
            tasks = listOf(TaskEntity(id = 1L, title = "数字电路")),
            courses = listOf(
                CourseEntity(
                    id = 1L,
                    name = "算法设计",
                    dayOfWeek = 3,
                    startTime = 600,
                    endTime = 690,
                    color = 0xFF112233.toInt(),
                    weeks = "1-8,10-16",
                ),
            ),
        )

        val text = backupJson.encodeToString(BackupPayload.serializer(), payload)
        val decoded = backupJson.decodeFromString(BackupPayload.serializer(), text)

        assertEquals(payload, decoded)
        assertEquals(4, decoded.totalRecordCount)
        // weeks 这种「非连续区间」表达必须原样往返，周次过滤依赖它
        assertEquals("1-8,10-16", decoded.courses.single().weeks)
    }

    @Test
    fun `学期表 round-trip 保留起止日期`() {
        val payload = BackupPayload(
            semesters = listOf(
                SemesterEntity(
                    id = 1L,
                    name = "2026 秋",
                    startDate = 20_600L,
                    endDate = 20_700L,
                    isCurrent = true,
                ),
            ),
        )

        val text = backupJson.encodeToString(BackupPayload.serializer(), payload)
        val decoded = backupJson.decodeFromString(BackupPayload.serializer(), text)

        assertEquals(payload, decoded)
        assertEquals(20_600L, decoded.semesters.single().startDate)
        assertTrue(decoded.semesters.single().isCurrent)
    }
}
