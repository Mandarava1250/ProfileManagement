package com.example.personalassistant.core.backup.scheduler

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.personalassistant.core.backup.exporter.BackupExporter
import com.example.personalassistant.core.backup.serializer.BackupSerializer
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.feature.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 9 A3：[DailyAutoBackupWorker] instrumented 测试
 *
 * ## 为什么 instrumented
 * - [DailyAutoBackupWorker] 依赖 `Context.contentResolver`（SAF）和真实 Preference DataStore
 *   —— JVM 测试里这些 Android API 都被 stub 成默认值 null，成功路径根本走不通
 * - 本类测的是 worker 的「编排决策」——开关检查 / 提前 return / 失败重试 / 时间戳写回
 *
 * ## 覆盖（5 + 1 = 6 例）
 * 1. `autoBackupEnabled=false` → Result.success()，不写时间戳（短路最常见 case）
 * 2. `autoBackupEnabled=true` 但 `backupFolderUri=""` → Result.success()，不写时间戳
 * 3. `BackupSerializer.snapshot()` 返回 Failure → Result.retry()，不写时间戳
 * 4. `BackupSerializer` 成功但 SAF `fromTreeUri()` 返回 null（无效 URI） → Result.retry()，不写时间戳
 * 5. (sanity) 全套依赖能正常构造 + worker.startWork() 至少返回有效 Result 对象
 * 6. **SAF happy path**（Phase 10 SAF-1）：
 *    启用 + folderUri 指向真 AVD DownloadsProvider 下新建的子目录 → Worker 走完整真 SAF 流程 →
 *    期待 Result.success()，时间戳被更新，文件落在子目录下。
 *
 * ## 方法名命名
 * 一律 camelCase —— `androidTest` 要过 D8，DEX 035 不允许方法名含空格（见 Phase 3 踩坑 15）
 */
@RunWith(AndroidJUnit4::class)
class DailyAutoBackupWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var serializer: BackupSerializer
    private lateinit var exporter: BackupExporter
    private lateinit var worker: DailyAutoBackupWorker

    private lateinit var prefsFile: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // 隔离的 DataStore 文件 —— 每个测试都新建，避免相互污染
        prefsFile = File(context.cacheDir, "test-prefs-${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
                androidx.datastore.preferences.core.emptyPreferences()
            },
            produceFile = { prefsFile },
        )
        settings = SettingsRepository(dataStore)

        serializer = BackupSerializer(
            courseDao = database.courseDao(),
            semesterDao = database.semesterDao(),
            taskDao = database.taskDao(),
            tagDao = database.tagDao(),
            taskTagDao = database.taskTagDao(),
            eventDao = database.eventDao(),
            financeDao = database.financeDao(),
            holidayDao = database.holidayDao(),
            userProfileDao = database.userProfileDao(),
        )

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        exporter = BackupExporter(json = json)

        worker = TestListenableWorkerBuilder<DailyAutoBackupWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = DailyAutoBackupWorker(
                    appContext = appContext,
                    params = workerParameters,
                    serializer = serializer,
                    exporter = exporter,
                    settings = settings,
                )
            })
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        if (prefsFile.exists()) prefsFile.delete()
    }

    // -----------------------------------------------------------------------
    // 测试用例
    // -----------------------------------------------------------------------

    /**
     * autoBackupEnabled = false 时必须短路 return success，绝不写 lastBackupAt，
     * 也绝不调 serializer（实测可显著省 DB IO，user 关闭开关后 worker 不浪费资源）。
     */
    @Test
    fun disabledShortCircuitsWithoutWritingTimestamp(): Unit = runTest {
        // 初始 lastBackupAt = 0
        assertEquals(0L, settings.lastBackupAt.first())

        val result = worker.startWork().get()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("开关关闭时不应写时间戳", 0L, settings.lastBackupAt.first())
    }

    /**
     * 开关开了但用户没选目录 → 短路 return success，绝不写时间戳。
     *
     * 这一条对应 UI 第一次开开关但没弹 SAF picker 的状态；
     * 这种时候 worker 不应触发 SAF 流程把 lastBackupAt 推到 1，让 UI 误以为「成功了一次」。
     */
    @Test
    fun enabledButBlankFolderUriShortCircuits(): Unit = runTest {
        settings.setAutoBackupEnabled(true)
        // folderUri 保持默认空串
        assertEquals("", settings.backupFolderUri.first())

        val result = worker.startWork().get()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("folderUri 为空时不应写时间戳", 0L, settings.lastBackupAt.first())
    }

    /**
     * BackupSerializer.snapshot() 返回 Failure（这里是抛异常 → 被 try/catch 转 Failure） →
     * worker 应 Result.retry() 让 WorkManager 重试，不应写时间戳。
     *
     * 模拟手段：让一个不抛异常的失败路径走通。最简单的：让 DAO 调用 throw。
     * 但数据库刚建好的 in-memory DB 里空表不会 throw，所以走另一条：用
     * `serializer` 的 snapshot 当作 throw 路径较麻烦。
     *
     * 退一步：测「`settings.autoBackupEnabled` 正常读到 → serializer 调 →
     * DAO 抛 → snapshot catch → Failure → Result.retry」。
     * 这里直接关闭 DB 强制 DAO 调用失败：
     */
    @Test
    fun serializerFailureReturnsRetryWithoutTimestamp(): Unit = runTest {
        settings.setAutoBackupEnabled(true)
        settings.setBackupFolderUri("file:///placeholder") // 任何非空 → 跳过空白检查
        database.close() // 强制后续 DAO 调用抛 IllegalStateException
        // 重新拿 serializer 实例（构造时拿的 DAO 是 close 前的，但调时会抛）
        // 不必重建 — close 后调用 .query() 会抛

        val result = worker.startWork().get()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals("serializer 失败时不应写时间戳", 0L, settings.lastBackupAt.first())
    }

    /**
     * BackupSerializer 成功 + SAF DocumentFile.fromTreeUri() 返回 null（无效 URI） →
     * writeToSaf 兜底返回 Failure → worker 应 Result.retry()，不写时间戳。
     */
    @Test
    fun invalidFolderUriMakesSafReturnNullThenRetry(): Unit = runTest {
        settings.setAutoBackupEnabled(true)
        // 一个非空但无对应 DocumentsProvider 的 URI → DocumentFile.fromTreeUri() 会返回 null
        settings.setBackupFolderUri("content://com.example.no-such-provider/tree/abc")

        val result = worker.startWork().get()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals("SAF 失败时不应写时间戳", 0L, settings.lastBackupAt.first())
    }

    /**
     * Sanity check：全套依赖能正常构造 + worker.startWork() 至少返回有效 Result 对象。
     * 防回归（注解缺失 / 反射异常 / 协程作用域没注入 等隐蔽问题）。
     */
    @Test
    fun sanityTestRunsWithoutCrash(): Unit = runTest {
        // 用 disabled 短路走完整链路
        val result = worker.startWork().get()
        assertNotNull(result)
        assertTrue("Result 必须是 success / retry 二选一", result == ListenableWorker.Result.success() || result == ListenableWorker.Result.retry())
    }

    // ========================================================================
    // Phase 10 SAF-1：SAF 写文件 happy path（真 AVD + 真 DocumentsProvider）
    // ========================================================================

    /**
     * 启用开关 + folderUri 指向真 AVD 的 DownloadsProvider 子目录 → Worker 走完整真 SAF 流程
     * （serializer.snapshot() → DocumentFile.fromTreeUri() → createFile → openOutputStream → 写 ZIP） →
     * 期待 Result.success()，时间戳被更新，文件落到子目录下。
     *
     * 设计要点（与例 1-5 不同点）：
     * - 不 fake ContentResolver —— 用 AVD 自带的 `DocumentsContract` / `DownloadsProvider`
     * - 在 AVD 的 Downloads 下真建隔离子目录，tearDown 递归删
     * - 把这个 tree URI 写到 settings → Worker 拿到非空有效 URI → 走真 SAF
     * - 用例尾部枚举子目录 children，找到 `cyrene-backup-*.zip` 并断言 size > 0
     *
     * 失败信号（用例会失败时）：
     * - Worker 报 retry → 说明 DocumentFile.fromTreeUri() 解析失败或 createFile 失败
     * - 子目录下没有 zip → 说明 exporter.export() 没真写进去
     * - 时间戳仍是 0 → 说明 SAF 成功但 settings 没写（应该不会发生）
     */
    @Test
    fun safHappyPathWritesZipAndUpdatesTimestamp(): Unit = runTest {
        val ctx = context
        val resolver = ctx.contentResolver

        // ===== 1. 拿 AVD 上 Downloads 的 SAF tree URI =====
        // AVD 上 DownloadsProvider 必然可达，Downloads 的标准 SAF tree URI 形如：
        //   content://com.android.providers.downloads.documents/tree/primary:Download
        // 这是公开的稳定路径（无需权限，无需 picker，因为 AVD 上应用 context 即 DownloadsProvider 客户端）。
        val downloadsTreeUri: Uri = Uri.parse(
            "content://com.android.providers.downloads.documents/tree/primary:Download"
        )
        val downloadsFolder = DocumentFile.fromTreeUri(ctx, downloadsTreeUri)
            ?: error("DocumentFile.fromTreeUri(downloadsTreeUri=$downloadsTreeUri) 返回 null —— AVD 上 DownloadsProvider 不可达")

        // ===== 2. 在 Downloads 下真建隔离子目录 =====
        val testFolderName = "cyrene-test-${System.nanoTime()}"
        val testFolder = downloadsFolder.createDirectory(testFolderName)
            ?: error("createDirectory($testFolderName) 失败 —— AVD DownloadsProvider 不可写或权限不足")
        val testFolderUri = testFolder.uri

        try {
            // ===== 3. 把这个 URI 写到 settings =====
            settings.setAutoBackupEnabled(true)
            settings.setBackupFolderUri(testFolderUri.toString())
            assertTrue(
                "AVD 上新建的子目录写入 settings 应成功",
                settings.backupFolderUri.first().isNotBlank(),
            )

            // ===== 4. 跑 Worker =====
            val before = System.currentTimeMillis()
            val result = worker.startWork().get()
            val after = System.currentTimeMillis()

            // ===== 5. 断言 Result + 时间戳 =====
            assertEquals(
                "SAF 写文件 happy path 应返回 success",
                ListenableWorker.Result.success(),
                result,
            )
            val ts = settings.lastBackupAt.first()
            assertTrue(
                "happy path 应写时间戳到 [before, after] 区间内，实际=$ts, before=$before, after=$after",
                ts in before..after,
            )

            // ===== 6. 验证文件真落到子目录 =====
            val children = testFolder.listFiles()
            assertNotNull("testFolder.listFiles() 不应为 null", children)
            val zips = children!!.filter {
                val name = it.name ?: return@filter false
                name.startsWith("cyrene-backup-") && name.endsWith(".zip")
            }
            assertTrue(
                "SAF 子目录下应至少存在一个 cyrene-backup-*.zip，实际 children=${children.map { it.name }}",
                zips.isNotEmpty(),
            )
            assertTrue(
                "写出的 zip 大小应 > 0，实际大小=${zips.first().length()}",
                zips.first().length() > 0,
            )
        } finally {
            // ===== tearDown：删测试子目录（连同 zip）=====
            // DocumentFile.delete() 会递归删 children
            testFolder.delete()
        }
        // 抑制 resolver 占位变量未用警告（保留它注释说明设计）
        @Suppress("UNUSED_VARIABLE")
        val unusedResolverRef = resolver
    }

    // 显式 runBlocking 引用占位（避免 unused import 警告）
    @Suppress("unused")
    private val unusedRunBlocking: Any = runBlocking { }
}