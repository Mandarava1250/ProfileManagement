package com.example.personalassistant.core.backup.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personalassistant.core.backup.exporter.BackupExporter
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.backup.validator.BackupValidator
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.profile.data.CurrentAccountRepository
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Phase 9 A1：[BackupImporter] 的 instrumented 真跑回归
 *
 * 覆盖 JVM 单测（[com.example.personalassistant.core.backup.model.BackupLegacyCompatTest]）给不出的四条链：
 * 1. ZIP 字节 → [BackupValidator] → [com.example.personalassistant.core.backup.validator.ValidationBundle] → [BackupPayload] 解码
 * 2. [BackupPayload] → Room 13 张表原子事务写入（含 PRAGMA foreign_keys OFF/ON 切换）
 * 3. 整表替换：第二次导入会清掉旧行
 * 4. 旧备份兼容：user_profiles 为空 → 现有账户不清空（plan §0.3 P3 关键不变量）
 * 5. 随机字节（非 ZIP）→ [BackupError.INVALID_MANIFEST]（与生产路径同源）：
 *    注：`BackupImporter` 只在 [BackupValidator] **抛 IOException** 时才返回 INVALID_ZIP；
 *    `ZipInputStream` 对纯文本宽容（静默返回空 entries），所以「烂文本」实际走 INVALID_MANIFEST。
 *    这是已知行为，本测试钉住它；想验 INVALID_ZIP 路径得喂会触发 `ZipInputStream` 抛异常的二进制头。
 *
 * ## 为什么走 instrumented（不是 JVM + Fake）
 * `import` / `importWithPreview` 是 `suspend` 且直接依赖 `AppDatabase`；
 * JVM 没有真 Room（项目没引 Robolectric），整条事务写入链根本没机会跑。
 * 用 `Room.inMemoryDatabaseBuilder` 在 AVD 上建一份真库，与生产同 schema。
 *
 * ## 跑法
 * AVD 需 `pm_api34`（aosp_atd / x86_64 / API 34）已启动：
 *   adb devices 非空
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *     com.example.personalassistant.core.backup.importer.BackupImporterTest
 *
 * ## 风格守则（与本仓库 androidTest 约定一致）
 * - 方法名一律 camelCase：DEX 035（minSdk 26）不允许方法名含空格（见 Phase 3 踩坑 15）
 * - runBlocking { } 末尾若返回非 Unit，**显式 : Unit** 兑住推断；JUnit4 会拒非 void 测试
 *   （见 [com.example.personalassistant.feature.profile.data.AvatarStorageImplTest] 顶部注释）
 */
@RunWith(AndroidJUnit4::class)
class BackupImporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // 与生产 BackupModule 提供的 @BackupJson 行为完全一致：忽略未知键 / 宽松 / encodeDefaults
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private lateinit var db: AppDatabase
    private lateinit var currentAccountRepo: FakeCurrentAccountRepository
    private lateinit var validator: BackupValidator
    private lateinit var importer: BackupImporter
    private lateinit var exporter: BackupExporter

    @Before
    fun setUp() {
        // in-memory DB：用现有 schema（v10）直接建，无需 migrations
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        currentAccountRepo = FakeCurrentAccountRepository(initialId = 1L)
        validator = BackupValidator(json)
        // 与生产 DI 注入同形：database + validator + currentAccountRepo + json
        importer = BackupImporter(db, validator, currentAccountRepo, json)
        exporter = BackupExporter(json)
    }

    @After
    fun tearDown(): Unit = db.close()

    // ====== 1. happy path：单账户 + 1 条流水 全量恢复 ======

    @Test
    fun importHappyPathInsertsAllRowsIntoRoom(): Unit = runBlocking {
        val payload = samplePayload(
            accounts = listOf(UserProfileEntity(id = 1L, name = "默认账户")),
            categories = listOf(
                CategoryEntity(id = 1L, accountId = 1L, name = "餐饮", type = 0),
                CategoryEntity(id = 2L, accountId = 1L, name = "工资", type = 1),
            ),
            transactions = listOf(
                TransactionEntity(
                    id = 1L,
                    accountId = 1L,
                    amount = 25.0,
                    type = 0,
                    categoryId = 1L,
                    date = 1_700_000_000_000L,
                    note = "午饭",
                ),
            ),
        )
        val bytes = exportToBytes(payload)

        val result = importer.import(ByteArrayInputStream(bytes))

        // ====== 1) BackupResult.Success，且 payload 内容回吐与送入一致 ======
        val success = result as BackupResult.Success
        assertEquals(1, success.value.userProfiles.size)
        assertEquals(2, success.value.categories.size)
        assertEquals(1, success.value.transactions.size)
        assertEquals("午饭", success.value.transactions[0].note)

        // ====== 2) DB 实际行数（这次写到的）======
        assertEquals(1, db.userProfileDao().getAll().size)
        assertEquals(2, db.financeDao().getAllCategories().size)
        assertEquals(1, db.financeDao().getAllTransactions().size)
        // 任务/课程/事件 等未在 payload 里的表，应仍为 0 行
        assertEquals(0, db.taskDao().getAllTasks().size)
        assertEquals(0, db.courseDao().getAllCourses().size)
        assertEquals(0, db.eventDao().getAllEvents().size)

        // ====== 3) 关键字段落库正确（避免「塞进去是塞了但值串了」）======
        val savedAcc = db.userProfileDao().getAll().first()
        assertEquals("默认账户", savedAcc.name)
        assertEquals(0.0, savedAcc.initialBalance, 0.0001)
        val savedTxn = db.financeDao().getAllTransactions().first()
        assertEquals(25.0, savedTxn.amount, 0.0001)
        assertEquals(0, savedTxn.type)
    }

    // ====== 2. 整表替换：第二次导入会清掉旧数据 ======

    @Test
    fun importReplacesExistingDataOnSecondRun(): Unit = runBlocking {
        val first = samplePayload(
            accounts = listOf(UserProfileEntity(id = 1L, name = "默认账户")),
            categories = listOf(CategoryEntity(id = 1L, accountId = 1L, name = "餐饮", type = 0)),
            transactions = listOf(
                TransactionEntity(
                    id = 1L,
                    accountId = 1L,
                    amount = 10.0,
                    type = 0,
                    categoryId = 1L,
                    date = 1_700_000_000_000L,
                ),
            ),
        )
        val second = samplePayload(
            accounts = listOf(
                UserProfileEntity(id = 1L, name = "默认账户"),
                UserProfileEntity(id = 2L, name = "副卡"),
            ),
            categories = listOf(
                CategoryEntity(id = 1L, accountId = 1L, name = "餐饮", type = 0),
                CategoryEntity(id = 2L, accountId = 1L, name = "工资", type = 1),
                CategoryEntity(id = 3L, accountId = 2L, name = "餐饮", type = 0),
            ),
            transactions = emptyList(),
        )

        // 第一次导入
        val r1 = importer.import(ByteArrayInputStream(exportToBytes(first)))
        assertTrue("第一次导入应成功", r1 is BackupResult.Success)
        assertEquals(1, db.financeDao().getAllTransactions().size)
        assertEquals(1, db.userProfileDao().getAll().size)

        // 第二次导入（不同账户集合 + 空 transactions）
        val r2 = importer.import(ByteArrayInputStream(exportToBytes(second)))
        assertTrue("第二次导入应成功", r2 is BackupResult.Success)

        // 第二次后：transactions 应被清空、accounts 应为 2 个、categories 应为 3
        assertEquals("transactions 应被清空", 0, db.financeDao().getAllTransactions().size)
        assertEquals("accounts 应被新集合替换", 2, db.userProfileDao().getAll().size)
        assertEquals("categories 应为 3 条", 3, db.financeDao().getAllCategories().size)

        // ====== 关键不变量：当前账户在重新导入后被 reconcile ======
        // 第二次导入后 user_profiles 全部替换（id=1 和 id=2），旧 id=1 还在 → 不会改 currentAccountId
        assertEquals("currentAccountId 应仍为 1（id=1 仍在）", 1L, currentAccountRepo.currentAccountId())
    }

    // ====== 3. 旧备份兼容：user_profiles 空 → 现有账户不清空 ======

    @Test
    fun importWithEmptyUserProfilesKeepsExistingAccounts(): Unit = runBlocking {
        // 预置一个默认账户
        db.userProfileDao().insert(UserProfileEntity(id = 1L, name = "默认账户"))

        // 旧备份（formatVersion=1，没有 user_profiles.json）—— userProfiles 留空
        val legacyPayload = samplePayload(
            accounts = emptyList(),
            categories = listOf(CategoryEntity(id = 1L, accountId = 1L, name = "餐饮", type = 0)),
            transactions = listOf(
                TransactionEntity(
                    id = 1L,
                    accountId = 1L,
                    amount = 5.0,
                    type = 0,
                    categoryId = 1L,
                    date = 1_700_000_000_000L,
                ),
            ),
        )
        val r = importer.import(ByteArrayInputStream(exportToBytes(legacyPayload)))

        assertTrue("旧备份应能成功导入", r is BackupResult.Success)
        // ====== 关键不变量：旧备份不清空现有账户 ======
        val accs = db.userProfileDao().getAll()
        assertEquals("旧备份不应清空现有账户", 1, accs.size)
        assertEquals("默认账户名应保留", "默认账户", accs[0].name)
        // 流水 1 条成功（归到默认账户 id=1）
        assertEquals(1, db.financeDao().getAllTransactions().size)
    }

    // ====== 4. 随机字节（非 ZIP）→ INVALID_MANIFEST ======
    //
    // 实际返回 INVALID_MANIFEST 而非 INVALID_ZIP，原因是 `ZipInputStream` 对纯文本宽容：
    // 它找不到 ZIP header 就静默返回空 entries，让 validator 走到「manifest.json 不存在」分支。
    // 想验 INVALID_ZIP 路径得喂会触发 `ZipInputStream` 抛 IOException 的二进制头（不在本测试范围内）。
    // 见 [BackupValidator.validateAndExtract] 的 manifest 校验 + 顶部类注释第 5 条。
    @Test
    fun importWithGarbageBytesReturnsInvalidManifest(): Unit = runBlocking {
        val garbage = "this is definitely not a zip file".toByteArray(Charsets.UTF_8)
        val r = importer.import(ByteArrayInputStream(garbage))
        assertTrue("非 ZIP 字节应返回 Failure，实际=$r", r is BackupResult.Failure)
        assertEquals(BackupError.INVALID_MANIFEST, (r as BackupResult.Failure).error)
    }

    // ====== helpers ======

    private fun samplePayload(
        accounts: List<UserProfileEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
    ): BackupPayload = BackupPayload(
        userProfiles = accounts,
        categories = categories,
        transactions = transactions,
    )

    /** 用真 [BackupExporter] 把 payload 写成 ZIP 字节流 —— 与生产路径同源 */
    private fun exportToBytes(payload: BackupPayload): ByteArray {
        val out = ByteArrayOutputStream()
        val r = exporter.export(payload, out)
        assertTrue("exporter 应成功，实际=$r", r is BackupResult.Success)
        return out.toByteArray()
    }

    /**
     * 内存版 CurrentAccountRepository —— 仅记录当前 id + 暴露 Flow
     * 与生产 [com.example.personalassistant.feature.profile.data.CurrentAccountRepositoryImpl] 行为一致
     */
    private class FakeCurrentAccountRepository(initialId: Long) : CurrentAccountRepository {
        private val state = MutableStateFlow(initialId)
        override suspend fun currentAccountId(): Long = state.value
        override fun currentAccountIdFlow(): Flow<Long> = state.asStateFlow()
        override suspend fun setCurrentAccountId(accountId: Long) {
            state.value = accountId
        }
    }
}
