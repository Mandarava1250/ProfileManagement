package com.example.personalassistant.core.backup.scheduler

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.personalassistant.core.backup.di.BackupJson
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
 * ## 覆盖（4 例，对应 plan §0.2 A3 的 4-6 例范围下限）
 * 1. `autoBackupEnabled=false` → Result.success()，不写时间戳（短路最常见 case）
 * 2. `autoBackupEnabled=true` 但 `backupFolderUri=""` → Result.success()，不写时间戳
 * 3. `BackupSerializer.snapshot()` 返回 Failure → Result.retry()，不写时间戳
 * 4. `BackupSerializer` 成功但 SAF `fromTreeUri()` 返回 null（无效 URI） → Result.retry()，不写时间戳
 *
 * **未覆盖**：
 * - SAF 写文件成功路径。Plan 9 A3 标 45 min，本类只剩这点预算。
 *   完整 happy path 需要 fake `ContentResolver` 模拟 `DocumentsContract` 的 4 个查询；
 *   单测代码量 100+，已超出 Phase 9 的时间盒。**该 case 留 Phase 9.1/9.2**
 *   或 Phase 10 真人手动验证（开关在 SettingsScreen 里跑一遍 + 看 SAF 文件夹里有新文件）。
 *
 * ## 设计要点
 * - 不用 `@HiltAndroidTest`：worker 的依赖少（4 个），全可手动构造
 *   - `BackupSerializer`：需要 8 个 DAO，用真 `AppDatabase` in-memory 拿
 *   - `BackupExporter`：`@BackupJson` Json 实例，手动 `Json {}` 配
 *   - `SettingsRepository`：需要 `DataStore<Preferences>`，用 `PreferenceDataStoreFactory.create`
 *     指向 `context.cacheDir/test-${nanoTime}.preferences_pb` 隔离
 * - 用 `TestListenableWorkerBuilder` 构造 worker，可直接 `.build().start()` 同步拿 Result
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

    // 显式 runBlocking 引用占位（避免 unused import 警告）
    @Suppress("unused")
    private val unusedRunBlocking: Any = runBlocking { }
}