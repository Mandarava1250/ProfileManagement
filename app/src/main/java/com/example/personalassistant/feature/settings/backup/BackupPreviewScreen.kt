package com.example.personalassistant.feature.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.AppTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.personalassistant.core.backup.model.BackupPreview
import com.example.personalassistant.core.backup.model.ImportStrategy
import com.example.personalassistant.core.backup.model.TableDiff
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 8 Sub-step 4'（plan 8f'）：备份导入预览 UI
 *
 * ## 流程
 * 1. 进入屏幕 → 自动拉起 SAF 文件选择器（用户从「恢复备份 → 预览后导入」跳转过来）
 * 2. 用户选完 → VM.load() → 校验 + 解码 + 算 diff → PreviewUiState.Ready
 * 3. UI 渲染 13 张表的策略 + 顶部「全 ADD/OVERWRITE/SKIP」快捷按钮
 * 4. 用户调完策略 → 点「确认导入」→ VM.confirmImport() → 写库 + reconcileCurrentAccount
 * 5. 成功 → snackbar + onBack()；失败 → snackbar 显示错误信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupPreviewScreen(
    onBack: () -> Unit,
    viewModel: BackupPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF 选文件 launcher
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            // 用户取消 → 直接返回上一页
            onBack()
        } else {
            // 打开 InputStream 需要 Context；在 Composable 顶层取一次，
            // 这里的 lambda 是 Activity callback，不在 @Composable scope
            viewModel.load(uri) { contentUri ->
                context.contentResolver.openInputStream(contentUri!!)
            }
        }
    }

    // 进入屏幕立即触发 SAF 选文件
    LaunchedEffect(Unit) {
        if (state is BackupPreviewViewModel.PreviewUiState.Idle) {
            launcher.launch(arrayOf("application/zip"))
        }
    }

    // Imported 状态 → snackbar + 返回
    LaunchedEffect(state) {
        val s = state
        if (s is BackupPreviewViewModel.PreviewUiState.Imported) {
            snackbarHostState.showSnackbar("导入成功（${s.totalRecords} 条记录）")
            viewModel.reset()
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = { Text(stringResource(R.string.backup_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                BackupPreviewViewModel.PreviewUiState.Idle,
                BackupPreviewViewModel.PreviewUiState.Loading,
                BackupPreviewViewModel.PreviewUiState.Importing -> CenterLoading()
                is BackupPreviewViewModel.PreviewUiState.Ready -> PreviewContent(
                    preview = s.preview,
                    onStrategyChange = viewModel::setStrategy,
                    onAllStrategy = viewModel::setAllStrategy,
                    onConfirm = viewModel::confirmImport,
                )
                is BackupPreviewViewModel.PreviewUiState.Error -> {
                    CenterLoading()
                    LaunchedEffect(s) {
                        snackbarHostState.showSnackbar(
                            "解析失败：${s.message ?: s.error.name}"
                        )
                    }
                }
                is BackupPreviewViewModel.PreviewUiState.Imported -> CenterLoading()
            }
        }
    }
}

@Composable
private fun CenterLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.padding(top = 16.dp))
            Text("正在解析备份…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PreviewContent(
    preview: BackupPreview,
    onStrategyChange: (String, ImportStrategy) -> Unit,
    onAllStrategy: (ImportStrategy) -> Unit,
    onConfirm: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeaderSummary(preview) }
        item { QuickActions(onAllStrategy) }
        item {
            Text(
                "逐表策略",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(preview.perTable, key = { it.tableName }) { diff ->
            TableSection(diff = diff, onStrategyChange = onStrategyChange)
        }
        item {
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text("确认导入")
            }
        }
    }
}

@Composable
private fun HeaderSummary(preview: BackupPreview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("备份信息", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Text(
                "格式版本：${preview.formatVersion}　源 DB 版本：${preview.sourceDbVersion}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "创建时间：${formatTimestamp(preview.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.padding(top = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatColumn("新增行", preview.totalToAdd, MaterialTheme.colorScheme.primary)
                StatColumn("覆盖行", preview.totalToOverwrite, MaterialTheme.colorScheme.tertiary)
                StatColumn("跳过表", preview.totalSkippedTables, MaterialTheme.colorScheme.outline)
            }
            if (preview.totalSkippedByForeignKey > 0) {
                Spacer(modifier = Modifier.padding(top = 8.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text("其中因外键被自动 SKIP：${preview.totalSkippedByForeignKey} 张")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActions(onAllStrategy: (ImportStrategy) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("快捷操作", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickButton(
                    text = "全 ADD",
                        icon = Icons.Rounded.Add,
                        onClick = { onAllStrategy(ImportStrategy.ADD) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickButton(
                        text = "全 OVERWRITE",
                        icon = Icons.Rounded.Edit,
                        onClick = { onAllStrategy(ImportStrategy.OVERWRITE) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickButton(
                        text = "全 SKIP",
                        icon = Icons.Rounded.HourglassEmpty,
                        onClick = { onAllStrategy(ImportStrategy.SKIP) },
                        modifier = Modifier.weight(1f),
                    )
            }
        }
    }
}

@Composable
private fun QuickButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.padding(start = 4.dp))
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableSection(
    diff: TableDiff<*>,
    onStrategyChange: (String, ImportStrategy) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (diff.skippedByForeignKey) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = prettyTableName(diff.tableName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (diff.skippedByForeignKey) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text("因外键 SKIP", style = MaterialTheme.typography.labelSmall)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "新增 ${diff.toAdd.size} 行",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.padding(start = 12.dp))
                Text(
                    "覆盖 ${diff.toOverwrite.size} 行",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = diff.strategy == ImportStrategy.ADD && !diff.skippedByForeignKey,
                        enabled = !diff.skippedByForeignKey,
                        onClick = { onStrategyChange(diff.tableName, ImportStrategy.ADD) },
                        label = { Text("ADD") },
                    )
                    FilterChip(
                        selected = diff.strategy == ImportStrategy.OVERWRITE && !diff.skippedByForeignKey,
                        enabled = !diff.skippedByForeignKey,
                        onClick = { onStrategyChange(diff.tableName, ImportStrategy.OVERWRITE) },
                        label = { Text("OVERWRITE") },
                    )
                    FilterChip(
                        selected = diff.strategy == ImportStrategy.SKIP || diff.skippedByForeignKey,
                        enabled = !diff.skippedByForeignKey,
                        onClick = { onStrategyChange(diff.tableName, ImportStrategy.SKIP) },
                        label = { Text("SKIP") },
                    )
            }
            if (diff.sampleRowIds.isNotEmpty()) {
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = "示例 id：${diff.sampleRowIds.take(5).joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 表名 → 人类可读
 */
private fun prettyTableName(tableName: String): String = when (tableName) {
    "user_profiles" -> "账户"
    "semesters" -> "学期"
    "courses" -> "课程"
    "tags" -> "标签"
    "tasks" -> "任务"
    "sub_tasks" -> "子任务"
    "task_tag_cross_ref" -> "任务 ↔ 标签"
    "events" -> "事件"
    "categories" -> "分类"
    "monthly_budgets" -> "月度预算"
    "daily_pools" -> "零钱池"
    "transactions" -> "流水"
    "holidays" -> "节假日"
    else -> tableName
}

private fun formatTimestamp(ts: Long): String {
    if (ts <= 0L) return "未知"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(ts))
}