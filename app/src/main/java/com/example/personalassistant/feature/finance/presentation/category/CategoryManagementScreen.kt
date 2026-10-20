package com.example.personalassistant.feature.finance.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.presentation.components.ColorPickerSheet
import com.example.personalassistant.feature.finance.presentation.components.IconPickerSheet
import kotlinx.coroutines.launch

/**
 * C 重构阶段 8：分类管理页
 *
 * - 列出全部分类
 * - FAB 添加（弹 [AddCategoryDialog]，让用户选图标）
 * - 每行右侧删除（仅非默认分类）
 * - 每行文本可点 → [RenameCategoryDialog]
 * - 每行色块可点 → [ColorPickerSheet]
 * - 每行 Edit 按钮 → [IconPickerSheet]（沿用旧能力）
 *
 * Snackbar 反馈：根据 [Feedback] 三档选用合适的提示（当前统一 Success/Warning/Error 提示文案）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit,
    viewModel: CategoryManagementViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var iconEditingFor by remember { mutableStateOf<Category?>(null) }
    var renameTarget by remember { mutableStateOf<Category?>(null) }
    var colorEditingFor by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let { fb ->
            scope.launch {
                snackbarHostState.showSnackbar(message = fb.message)
                viewModel.clearFeedback()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加分类")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无分类", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(categories, key = { it.id }) { cat ->
                    CategoryRow(
                        category = cat,
                        onDelete = { pendingDelete = cat },
                        onEditIcon = { iconEditingFor = cat },
                        onRename = { renameTarget = cat },
                        onChangeColor = { colorEditingFor = cat },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, iconName, color ->
                viewModel.addCategory(name, type, iconName, color)
                showAddDialog = false
            },
        )
    }

    pendingDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分类？") },
            text = { Text("确定要删除「${cat.name}」吗？有交易引用时会拒绝。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(cat)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    iconEditingFor?.let { cat ->
        IconPickerSheet(
            selectedIconName = cat.iconName,
            onPick = { newName ->
                viewModel.setIcon(cat, newName)
                iconEditingFor = null
            },
            onDismiss = { iconEditingFor = null },
        )
    }

    renameTarget?.let { cat ->
        RenameCategoryDialog(
            initialName = cat.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.rename(cat, newName)
                renameTarget = null
            },
        )
    }

    colorEditingFor?.let { cat ->
        ColorPickerSheet(
            selectedColor = cat.color,
            onPick = { newColor ->
                viewModel.changeColor(cat, newColor)
                colorEditingFor = null
            },
            onDismiss = { colorEditingFor = null },
        )
    }
}

/** 改名轻量对话框：纯输入框 + 确认/取消 */
@Composable
private fun RenameCategoryDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名分类") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("新名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}