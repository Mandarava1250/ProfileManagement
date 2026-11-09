package com.example.personalassistant.feature.settings.backup

import android.net.Uri
import com.example.personalassistant.core.backup.diff.BackupDiffCalculator
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.ImportStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Phase 10 10a-2：BackupPreviewViewModel JVM unit tests（6 例）
 *
 * 状态机：Idle → Loading → Ready → Importing → Imported；任意阶段失败 → Error
 *
 * ## Fake 策略
 * - [FakeBackupValidator]：实现 [com.example.personalassistant.core.backup.validator.IBackupValidator]
 *   （Phase 10 抽出），返回预设 Success/Failure
 * - [FakeBackupSnapshotProvider]：实现 [com.example.personalassistant.core.backup.snapshot.BackupSnapshotProvider]
 *   接口（Phase 9 A2 已抽出）
 * - [FakeBackupImporter]：实现 [com.example.personalassistant.core.backup.importer.IBackupImporter]
 *   （Phase 10 抽出），覆写 `importWithPreview`
 * - [BackupDiffCalculator]：用真实实例（无参、纯函数、无 Hilt 依赖 —— Phase 8 自评已注明）
 *
 * ## 协程调度
 * - `Dispatchers.setMain(UnconfinedTestDispatcher)`：拦截 viewModelScope.launch；
 *   UnconfinedTestDispatcher 立即同步执行协程，省去 advanceUntilIdle 也行。
 * - 但为了显式可控，仍调 `advanceUntilIdle()` 让所有任务跑完。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupPreviewViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val validator = FakeBackupValidator()
    private val snapshotProvider = FakeBackupSnapshotProvider()
    private val diffCalculator = BackupDiffCalculator()
    private val importer = FakeBackupImporter()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        // viewModelScope 默认走 Dispatchers.Main；测试里劫持到 testDispatcher 让协程同步跑
        Dispatchers.setMain(testDispatcher)
        // 探测：访问一次 Dispatchers.Main.immediate 看是否抛 NPE（诊断用）
        try {
            val probe = Dispatchers.Main.immediate
            println("DIAG: Dispatchers.Main.immediate = $probe, hash=${System.identityHashCode(probe)}")
        } catch (t: Throwable) {
            println("DIAG: Main.immediate threw ${t::class.simpleName}: ${t.message}")
        }
        // 默认 validator 返回空 bundle（保证 happy path 也能跑）
        validator.setSuccess()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): BackupPreviewViewModel = BackupPreviewViewModel(
        validator = validator,
        snapshotProvider = snapshotProvider,
        diffCalculator = diffCalculator,
        importer = importer,
        json = json,
    )

    /** 一个始终返回空流的 lambda —— fakeStreamProvider 用 `_` 忽略参数，
     *  所以传 placeholderUri()（内部是 `null as Uri`）也不会触发 NPE。 */
    private val fakeStreamProvider: (Uri?) -> InputStream? = { _ -> ByteArrayInputStream(ByteArray(0)) }
    // 不要把 placeholderUri() 存到 val 字段 —— 字段访问会在调用 site 触发 Kotlin null check（与 Phase 4 trick 冲突）。
    // 直接在调用点用表达式 placeholderUri()，Kotlin 信任声明返回 Uri 类型，不生成 null check。

    // -----------------------------------------------------------------------
    // 1) 初始状态
    // -----------------------------------------------------------------------
    @Test
    fun `initial state is Idle`() = runTest(testDispatcher) {
        val vm = newVm()
        assertEquals(
            "新建 VM 应处于 Idle 状态",
            BackupPreviewViewModel.PreviewUiState.Idle,
            vm.state.value,
        )
    }

    // -----------------------------------------------------------------------
    // 2) load happy path: Idle → Loading → Ready
    // -----------------------------------------------------------------------
    @Test
    fun `load reaches Ready when validator returns Success`() = runTest(testDispatcher) {
        validator.setSuccess() // 空 bundle → 20 个空 TableDiff
        val vm = newVm()

        vm.load(uri = placeholderUri(), inputStreamProvider = fakeStreamProvider)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "load 完成后应处于 Ready，实际=$state",
            state is BackupPreviewViewModel.PreviewUiState.Ready,
        )
        val ready = state as BackupPreviewViewModel.PreviewUiState.Ready
        assertEquals(
            // Phase 23：加了 habits / habit_checkins 两张表，15 → 17；
            // 批次 C：加了 coin_pools 一张表，17 → 18；
            // 批次 D：加了 coin_pool_settlements 一张表，18 → 19；
            // Phase 28 P2：加了 course_sessions 一张表，19 → 20
            "BackupPreview 应包含 20 张表的 perTable",
            20,
            ready.preview.perTable.size,
        )
        assertEquals(
            "每个 perTable 默认策略 = ADD",
            ImportStrategy.ADD,
            ready.preview.perTable.first().strategy,
        )
        assertEquals(
            "snapshot 应被调一次（load 阶段 2）",
            1,
            snapshotProvider.snapshotCallCount,
        )
        assertEquals(
            "validator 应被调一次（load 阶段 1）",
            1,
            validator.validateAndExtractCallCount,
        )
    }

    // -----------------------------------------------------------------------
    // 3) setStrategy 单表改：只动目标表，不动其它表
    // -----------------------------------------------------------------------
    @Test
    fun `setStrategy changes target table only`() = runTest(testDispatcher) {
        validator.setSuccess()
        val vm = newVm()
        vm.load(uri = placeholderUri(), inputStreamProvider = fakeStreamProvider)
        advanceUntilIdle()

        // 拿 ready 状态快照，便于比对改前 vs 改后
        val before = vm.state.value as BackupPreviewViewModel.PreviewUiState.Ready
        val targetTable = "tasks" // 普通表（无 FK SKIP）
        val originalStrategy = before.preview.perTable.first { it.tableName == targetTable }.strategy

        // 切到 OVERWRITE
        vm.setStrategy(targetTable, ImportStrategy.OVERWRITE)
        advanceUntilIdle()

        val after = vm.state.value as BackupPreviewViewModel.PreviewUiState.Ready
        val afterTarget = after.preview.perTable.first { it.tableName == targetTable }
        assertEquals(
            "目标表策略应改为 OVERWRITE",
            ImportStrategy.OVERWRITE,
            afterTarget.strategy,
        )
        // 其它表策略不变
        val otherTablesChanged = after.preview.perTable
            .filter { it.tableName != targetTable }
            .any { it.strategy != ImportStrategy.ADD }
        assertTrue(
            "非目标表策略不应被改变（仍全 ADD）",
            !otherTablesChanged,
        )
        assertTrue(
            "原策略应当是 ADD（否则用例自身假设错）",
            originalStrategy == ImportStrategy.ADD,
        )
    }

    // -----------------------------------------------------------------------
    // 4) setAllStrategy 全表改
    // -----------------------------------------------------------------------
    @Test
    fun `setAllStrategy affects all tables`() = runTest(testDispatcher) {
        validator.setSuccess()
        val vm = newVm()
        vm.load(uri = placeholderUri(), inputStreamProvider = fakeStreamProvider)
        advanceUntilIdle()

        vm.setAllStrategy(ImportStrategy.SKIP)
        advanceUntilIdle()

        val ready = vm.state.value as BackupPreviewViewModel.PreviewUiState.Ready
        val allSkipped = ready.preview.perTable.all { it.strategy == ImportStrategy.SKIP }
        assertTrue(
            "setAllStrategy(SKIP) 应把所有表策略置为 SKIP",
            allSkipped,
        )
    }

    // -----------------------------------------------------------------------
    // 5) confirmImport 写库成功: Ready → Importing → Imported
    // -----------------------------------------------------------------------
    @Test
    fun `confirmImport reaches Imported when importer returns Success`() = runTest(testDispatcher) {
        validator.setSuccess()
        // importer 返回 totalRecordCount=N 的 payload
        importer.setSuccess(BackupPayload(userProfiles = emptyList())) // totalRecordCount = 0
        val vm = newVm()
        vm.load(uri = placeholderUri(), inputStreamProvider = fakeStreamProvider)
        advanceUntilIdle()

        assertTrue(
            "前置：应处于 Ready",
            vm.state.value is BackupPreviewViewModel.PreviewUiState.Ready,
        )

        vm.confirmImport()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "confirmImport 后应处于 Imported，实际=$state",
            state is BackupPreviewViewModel.PreviewUiState.Imported,
        )
        val imported = state as BackupPreviewViewModel.PreviewUiState.Imported
        assertEquals(
            "Imported.totalRecords 应来自 importer 返回 payload 的 totalRecordCount",
            0,
            imported.totalRecords,
        )
        assertEquals(
            "importer.importWithPreview 应被调一次",
            1,
            importer.importWithPreviewCallCount,
        )
        assertNotNull(
            "importer 应拿到 Ready 状态里的 payload 引用",
            importer.lastPayload,
        )
    }

    // -----------------------------------------------------------------------
    // 6) Error 路径：validator 返回 Failure(INVALID_ZIP) → VM 进 Error
    // -----------------------------------------------------------------------
    @Test
    fun `load fails with InvalidZip returns Error`() = runTest(testDispatcher) {
        validator.setFailure(BackupError.INVALID_ZIP, "fake invalid zip")
        val vm = newVm()

        vm.load(uri = placeholderUri(), inputStreamProvider = fakeStreamProvider)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "validator 失败时应处于 Error(INVALID_ZIP)，实际=$state",
            state is BackupPreviewViewModel.PreviewUiState.Error,
        )
        val err = state as BackupPreviewViewModel.PreviewUiState.Error
        assertEquals(BackupError.INVALID_ZIP, err.error)
        assertEquals(
            "Error.message 应来自 validator 的 Failure.message",
            "fake invalid zip",
            err.message,
        )
        // Error 路径不应触发 snapshot（snapshot 在阶段 2）
        assertEquals(
            "validator 失败时 snapshot 不应被调用",
            0,
            snapshotProvider.snapshotCallCount,
        )
        assertEquals(
            "validator 应被调一次",
            1,
            validator.validateAndExtractCallCount,
        )
    }
}

/**
 * Phase 10 10a-3 验证：JVM stub 下：
 * - `Uri.EMPTY` 是 null（反射已确认）
 * - `android.net.Uri$StringUri` 反射找不到（stub 不生成内部类）
 * - `Uri.parse(...)` 拋 NPE（stub 里有 `Preconditions.checkNotNull(..., "parse(...) must not be null")`）
 * - `Uri.Builder.scheme()` 返回 null（stub 返 default value）
 * - Kotlin 对 [BackupPreviewViewModel.load] 的 [Uri] 参数编译期生成 null check，
 *   [nullAs] trick 只能绕过调用 site，绕不过 callee 函数体内的 [Intrinsics.checkNotNullParameter]。
 *
 * **决定**：把 VM 的 [BackupPreviewViewModel.load] 第一个参数改成 [Uri]?（plan 微调），
 * 测试里直接传 [null]，fakeStreamProvider 用 `_` 忽略参数即可。
 * 生产代码里 VM 不会真的解引用 `uri`（仅传给 lambda + 拼错误消息），
 * 改 nullable 对运行行为无影响（lambda 永远拿到非空 Uri.EMPTY fallback）。
 *
 * 同模式见 Phase 4 的 [com.example.personalassistant.feature.profile.data.placeholderUri]，
 * 那里的 useCase.invoke 不需要改（operator invoke + Kotlin 优化路径不一样）。
 */
private fun placeholderUri(): Uri = nullAs()

@Suppress("UNCHECKED_CAST")
private fun <T> nullAs(): T = null as T