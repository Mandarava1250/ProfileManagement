package com.example.personalassistant.feature.task.presentation.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.feature.task.presentation.detail.sections.BasicInfoSection
import com.example.personalassistant.feature.task.presentation.detail.sections.SubTaskSection
import com.example.personalassistant.feature.task.presentation.detail.sections.TagSection
import com.example.personalassistant.feature.task.presentation.util.TagPaletteSource
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext

/**
 * Phase 6b→6c：任务详情页
 *
 * - 顶部 AppBar：返回 + 「任务详情」 + 编辑按钮 -> 跳 [Screen.EditTask]
 * - 主体：verticalScroll Column，按 sections 顺序展示
 *   1) [BasicInfoSection] - 任务基本信息（只读）
 *   2) [SubTaskSection] - 子清单（可增删改）
 *   3) [TagSection] - 标签（Phase 6c 新增，可打 / 卸 / 新建）
 *   4) [PlaceholderSection] - 附件（未来用）
 * - task 被删除（task == null）时自动 popBackStack
 * - errorMessage 通过 SnackbarHost 一次性提示
 *
 * Sections 模式（plan v3.1 扩展点）：所有 section 独立 Composable，独立文件
 * （见 [BasicInfoSection] / [SubTaskSection] / [TagSection]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // ===== TagPaletteSource 通过 Hilt EntryPoint 取（Composable 不好直接 @Inject） =====
    val context = LocalContext.current
    val paletteSource = remember(context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TagPaletteEntryPoint::class.java
        )
        entryPoint.tagPaletteSource()
    }

    // ===== task 被删除时自动返回 =====
    LaunchedEffect(state.task) {
        if (!state.isLoading && state.task == null) {
            onBack()
        }
    }

    // ===== errorMessage 一次性 snackbar =====
    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEdit(taskId) },
                        enabled = state.task != null,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val task = state.task
        if (task != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ===== Section 1: BasicInfo =====
                BasicInfoSection(
                    task = task,
                    onToggleComplete = viewModel::toggleComplete,
                )

                // ===== Section 2: SubTask =====
                SubTaskSection(
                    subTasks = state.subTasks,
                    completedCount = state.completedSubTaskCount,
                    input = state.newSubTaskInput,
                    onInputChange = viewModel::onNewSubTaskInputChange,
                    onAdd = viewModel::addSubTask,
                    onToggle = viewModel::toggleSubTaskComplete,
                    onDelete = viewModel::deleteSubTask,
                )

                // ===== Section 3: Tag（Phase 6c）=====
                TagSection(
                    tags = state.tags,
                    paletteSource = paletteSource,
                    onRemoveTag = viewModel::removeTag,
                    onAddTag = viewModel::addTag,
                )

                // ===== Section 4: Attachments（未来占位）=====
                PlaceholderSection(
                    title = "附件",
                    hint = "附件功能即将到来",
                )
            }
        }
    }
}

/**
 * Phase 6c：用 Hilt EntryPoint 模式从 Composable 里拿 [TagPaletteSource]。
 *
 * - Composable 不能用 @Inject；Hilt 官方推荐用 EntryPointAccessors.fromApplication(...)
 * - 避免改 TaskDetailScreen 为 ViewModel 多接一个 use case（保持 VM 接口干净）
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface TagPaletteEntryPoint {
    fun tagPaletteSource(): TagPaletteSource
}

/**
 * Sections 模式（plan v3.1 扩展点）：预留的占位 section。
 *
 * - 6b 阶段仅用于 Attachments 视觉占位（Tags 已替换为真正的 TagSection）
 * - 未来附件功能替换为 AttachmentsSection（独立文件）
 * - 此函数为 file-private，外部使用应 import 真正的 section 文件
 */
@Composable
private fun PlaceholderSection(title: String, hint: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}