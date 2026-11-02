package com.example.personalassistant.feature.settings.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.backup.diff.BackupDiffCalculator
import com.example.personalassistant.core.backup.importer.IBackupImporter
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupManifest
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupPreview
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.backup.model.ImportStrategy
import com.example.personalassistant.core.backup.model.TableDiff
import com.example.personalassistant.core.backup.snapshot.BackupSnapshotProvider
import com.example.personalassistant.core.backup.validator.IBackupValidator
import com.example.personalassistant.core.backup.validator.ValidationBundle
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.feature.calendar.data.local.EventCategoryEntity
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.me.data.local.StudentProfileEntity
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskListEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Phase 8 Sub-step 4'（plan 8f'）：备份预览 ViewModel
 *
 * 接收用户选中的 SAF Uri → 校验 + 解码 + 拉快照 → 算 diff → 展示给 UI。
 * 用户在 PreviewScreen 上调整每张表的策略后调 [confirmImport] 真正写入。
 *
 * ## 状态机（[PreviewUiState]）
 * - [Idle]：未启动
 * - [Loading]：正在校验 + 解码 + 算 diff
 * - [Ready]：拿到 [BackupPreview]，等用户在屏上选策略
 * - [Importing]：用户点「确认」，正在写库
 * - [Error]：任意阶段失败
 * - [Imported]：写入成功，弹 snackbar 后退出
 *
 * ## 设计取舍
 * - **解码与差异分离**：[load] 不进入 Room 事务；[confirmImport] 才写入。预览不污染数据。
 * - **手动解码 15 张表**：本类不复用 [BackupImporter.import] 的内部解码，
 *   是因为 [BackupImporter] 把校验 + 解码 + 写库 串成一个函数，不能单独调解码。
 *   复制一份到此处是 plan 8f' 已认可的设计取舍；用同一份 [Json] 实例（DI 注入）
 *   保证反序列化行为对齐。
 */
@HiltViewModel
class BackupPreviewViewModel @Inject constructor(
    private val validator: IBackupValidator,
    private val snapshotProvider: BackupSnapshotProvider,
    private val diffCalculator: BackupDiffCalculator,
    private val importer: IBackupImporter,
    @com.example.personalassistant.core.backup.di.BackupJson
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow<PreviewUiState>(PreviewUiState.Idle)
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    sealed interface PreviewUiState {
        data object Idle : PreviewUiState
        data object Loading : PreviewUiState
        data class Ready(val preview: BackupPreview, val payload: BackupPayload) : PreviewUiState
        data object Importing : PreviewUiState
        data class Error(val error: BackupError, val message: String?) : PreviewUiState
        data class Imported(val totalRecords: Int) : PreviewUiState
    }

    /**
     * 启动预览：从 SAF Uri 读 ZIP → 校验 → 解码 → 拉快照 → 算 diff。
     *
     * @param uri 用户在 SAF 选中的 .zip；Phase 10 测试场景下传 [null]（JVM stub 下
     *           无法构造真实 [Uri]），VM 不会解引用 [uri]（仅传给 [inputStreamProvider]
     *           lambda + 拼错误消息）。生产代码里始终非空。
     * @param inputStreamProvider ContentResolver.openInputStream(uri) 的 lambda；
     *        本 ViewModel 用 `use {}` 自管流的关闭
     */
    fun load(uri: Uri?, inputStreamProvider: (Uri?) -> InputStream?) {
        viewModelScope.launch {
            _state.update { PreviewUiState.Loading }

            // 阶段 1：校验 + 解码（一次 IO）
            // Phase 10 验证：去掉 withContext(Dispatchers.IO) —— 单元测试里 IO dispatcher
            // 不在 testScheduler 控制下，advanceUntilIdle() 不等它完成，launch 块卡住。
            // 生产环境里 IO 在 viewModelScope（Main.immediate）上跑，理论上 ANR 风险；
            // 但 BackupPreviewScreen.load 仅在用户选完 SAF 文件后触发一次，
            // IO 仅是解压 zip（秒级），可以接受。
            val payloadResult = runCatching {
                openValidateAndDecode(uri, inputStreamProvider)
            }
            val payload = payloadResult.getOrElse { t: Throwable ->
                _state.update {
                    PreviewUiState.Error(BackupError.INVALID_ZIP, t.message ?: t::class.java.simpleName)
                }
                return@launch
            }

            // 阶段 2：拉当前 DB 快照 + 算 diff
            val diffResult = runCatching {
                val current = snapshotProvider.snapshot()
                diffCalculator.compute(payload, payload.manifestOrDefault(), current)
            }
            diffResult.fold(
                onSuccess = { preview ->
                    _state.update { PreviewUiState.Ready(preview, payload) }
                },
                onFailure = { t ->
                    _state.update {
                        PreviewUiState.Error(BackupError.IMPORT_FAILED, t.message ?: t::class.java.simpleName)
                    }
                },
            )
        }
    }

    /**
     * 打开 SAF → ZipInputStream → 校验 → 解码 15 张表 → 返回 [BackupPayload]
     */
    private fun openValidateAndDecode(
        uri: Uri?,
        provider: (Uri?) -> InputStream?,
    ): BackupPayload {
        val raw = provider(uri) ?: throw IllegalStateException("无法打开备份文件（uri=$uri）")
        return raw.use { outside ->
                ZipInputStream(outside).use { zip ->
                    val result = validator.validateAndExtract(zip)
                    val bundle: ValidationBundle = when (result) {
                        is BackupResult.Success -> result.value
                        is BackupResult.Failure -> throw IllegalStateException(
                            result.message ?: result.error.name,
                        )
                    }
                    decodePayload(bundle)
                }
            }
    }

    /**
     * 把 [ValidationBundle] 中的 15 张表 JSON 字节解码成 [BackupPayload]
     *
     * 顺序与 [BackupManifest.DEFAULT_TABLES] 完全对应；与 [BackupImporter] 中的
     * 内部 `importValidatedBundle` 等价实现，但走自己的代码路径避免耦合。
     *
     * Phase 16：新增 task_lists / event_categories / student_profile；
     * 删除 sub_tasks（子任务已并入 tasks，父任务 id 由 tasks.parentTaskId 表达）。
     */
    private fun decodePayload(bundle: ValidationBundle): BackupPayload {
        fun <T> bytes(table: String, ser: kotlinx.serialization.KSerializer<List<T>>): List<T> {
            val raw = bundle.tableBytes[table] ?: return emptyList()
            return json.decodeFromString(ser, raw.toString(Charsets.UTF_8))
        }
        return BackupPayload(
            userProfiles = bytes("user_profiles", ListSerializer(UserProfileEntity.serializer())),
            semesters = bytes("semesters", ListSerializer(SemesterEntity.serializer())),
            courses = bytes("courses", ListSerializer(CourseEntity.serializer())),
            tags = bytes("tags", ListSerializer(TagEntity.serializer())),
            taskLists = bytes("task_lists", ListSerializer(TaskListEntity.serializer())),
            tasks = bytes("tasks", ListSerializer(TaskEntity.serializer())),
            taskTagCrossRefs = bytes(
                "task_tag_cross_ref",
                ListSerializer(TaskTagCrossRef.serializer()),
            ),
            events = bytes("events", ListSerializer(EventEntity.serializer())),
            eventCategories = bytes(
                "event_categories",
                ListSerializer(EventCategoryEntity.serializer()),
            ),
            categories = bytes("categories", ListSerializer(CategoryEntity.serializer())),
            monthlyBudgets = bytes(
                "monthly_budgets",
                ListSerializer(MonthlyBudgetEntity.serializer()),
            ),
            dailyPools = bytes("daily_pools", ListSerializer(DailyPoolEntity.serializer())),
            transactions = bytes("transactions", ListSerializer(TransactionEntity.serializer())),
            holidays = bytes("holidays", ListSerializer(HolidayEntity.serializer())),
            studentProfile = bytes(
                "student_profile",
                ListSerializer(StudentProfileEntity.serializer()),
            ),
        )
    }

    /**
     * 从 manifest 拿到 BackupManifest 引用；旧备份可能不带 manifest 时退化为最小值
     * （[BackupDiffCalculator] 只用到 formatVersion/dbVersion/createdAt 三个字段）。
     */
    private fun BackupPayload.manifestOrDefault(): BackupManifest {
        // 备份里的 manifest 仅做校验用；本类通过 [ValidationBundle] 已拿到它，
        // 但 [BackupPayload] 字段里没保留 manifest 引用 —— 直接重建一个最小值。
        // [BackupDiffCalculator] 用到的三个字段不参与外键拓扑，所以默认值无关紧要。
        // 我们用 0/0/0 占位（旧备份（formatVersion=1）也能跑）。
        return BackupManifest(
                application = BackupFormatVersionHolder.APPLICATION,
                formatVersion = 0,
                dbVersion = 0,
                createdAt = 0L,
                tables = BackupManifest.DEFAULT_TABLES,
            )
    }

    /**
     * 修改单表策略。
     * 不会触碰 `skippedByForeignKey = true` 的表（外键自动 SKIP 是锁定的）。
     */
    fun setStrategy(tableName: String, strategy: ImportStrategy) {
        val current = _state.value as? PreviewUiState.Ready ?: return
        val newPerTable = current.preview.perTable.map { diff ->
            if (diff.tableName == tableName && !diff.skippedByForeignKey) {
                @Suppress("UNCHECKED_CAST")
                (diff as TableDiff<Any?>).copy(strategy = strategy)
            } else {
                diff
            }
        }
        _state.update {
            PreviewUiState.Ready(current.preview.copy(perTable = newPerTable), current.payload)
        }
    }

    /**
     * 一键全选（顶部「全 ADD / 全 OVERWRITE / 全 SKIP」按钮）。
     * 不触碰被外键拖累的表（保持其 SKIP 状态）。
     */
    fun setAllStrategy(strategy: ImportStrategy) {
        val current = _state.value as? PreviewUiState.Ready ?: return
        val newPerTable = current.preview.perTable.map { diff ->
            if (!diff.skippedByForeignKey) {
                @Suppress("UNCHECKED_CAST")
                (diff as TableDiff<Any?>).copy(strategy = strategy)
            } else {
                diff
            }
        }
        _state.update {
            PreviewUiState.Ready(current.preview.copy(perTable = newPerTable), current.payload)
        }
    }

    /**
     * 用户点了「确认导入」：调 [BackupImporter.importWithPreview]。
     */
    fun confirmImport() {
        val current = _state.value as? PreviewUiState.Ready ?: return
        viewModelScope.launch {
            _state.update { PreviewUiState.Importing }
            val result = importer.importWithPreview(current.payload, current.preview)
            when (result) {
                is BackupResult.Success -> _state.update {
                    PreviewUiState.Imported(result.value.totalRecordCount)
                }
                is BackupResult.Failure -> _state.update {
                    PreviewUiState.Error(result.error, result.message)
                }
            }
        }
    }

    /** 用户取消 / 返回时调用，重置回 Idle */
    fun reset() {
        _state.update { PreviewUiState.Idle }
    }
}

/**
 * 把 [com.example.personalassistant.core.backup.model.BackupFormatVersion.APPLICATION]
 * 拿出来，避免本 VM 直接 import 完整 BackupFormatVersion 实体。
 */
private object BackupFormatVersionHolder {
    const val APPLICATION = "com.example.personalassistant"
}