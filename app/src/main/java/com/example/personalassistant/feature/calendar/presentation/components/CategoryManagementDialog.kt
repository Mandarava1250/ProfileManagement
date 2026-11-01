package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.calendar.domain.model.EventCategory

/**
 * Phase 8 Sub-step 9：事件分类管理 Sheet（CRUD + 颜色）
 *
 * - 用 AlertDialog 装（简单、稳定、不引新依赖）
 * - 列表显示：色块 + 名称 + 排序值 + 「编辑/删除/默认锁」按钮
 * - 「+ 新建」入口
 * - 编辑/新建复用同一个子 Dialog（[CategoryEditDialog]）
 *
 * ## 为什么不引 BottomSheet？
 * - 项目现有 EditEventSheet 是 ModalBottomSheet，但分类管理是"独立子页面"性质
 * - AlertDialog 实现简单，足以装下分类列表 + 嵌套编辑
 *
 * ## 状态管理
 * - 这个 Composable 自身维护「正在编辑的分类 id」（editingCategory）和「新建草稿」状态
 * - 父组件不感知，只通过 callbacks 接收结果
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementDialog(
    categories: List<EventCategory>,
    onDismiss: () -> Unit,
    onUpsert: (EventCategory) -> Unit,
    onDelete: (Long) -> Unit,
    errorMessage: String? = null,
    onClearError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var editingCategory by remember { mutableStateOf<EventCategory?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("事件分类管理", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 错误提示
                errorMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // 分类列表
                if (categories.isEmpty()) {
                    Text(
                        "暂无分类，点击下方「新建」按钮添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(categories, key = { it.id }) { category ->
                            CategoryRow(
                                category = category,
                                onEdit = { editingCategory = category },
                                onDelete = { onDelete(category.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { isCreating = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("新建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )

    // ===== 新建分类 =====
    if (isCreating) {
        CategoryEditDialog(
            initial = null,
            existingNames = categories.map { it.name }.toSet(),
            errorMessage = errorMessage,
            onDismiss = {
                isCreating = false
                onClearError()
            },
            onConfirm = { category ->
                onUpsert(category)
                isCreating = false
            },
        )
    }

    // ===== 编辑已有分类 =====
    editingCategory?.let { cat ->
        CategoryEditDialog(
            initial = cat,
            existingNames = categories.map { it.name }.toSet(),
            errorMessage = errorMessage,
            onDismiss = {
                editingCategory = null
                onClearError()
            },
            onConfirm = { category ->
                onUpsert(category)
                editingCategory = null
            },
        )
    }
}

/**
 * 单行分类展示
 */
@Composable
private fun CategoryRow(
    category: EventCategory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 色块
        Box(
            modifier = Modifier
                .size(20.dp)
                .padding(end = 8.dp),
        ) {
            Surface(
                color = Color(category.color),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            ) {}
        }
        Spacer(Modifier.size(8.dp))
        // 名称
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (category.isDefault) "系统内置" else "自定义",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 编辑按钮
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack, // 任意图标；项目里 ArrowBack 一定有
                contentDescription = "编辑 ${category.name}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        // 删除按钮（默认不可删）
        if (!category.isDefault) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除 ${category.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 分类编辑子 Dialog（新建/编辑复用）
 *
 * - [initial] == null → 新建模式；否则为编辑模式（带回显 + 锁定 isDefault）
 * - [existingNames] 用于重名校验（排除自身）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditDialog(
    initial: EventCategory?,
    existingNames: Set<String>,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (EventCategory) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: 0xFF6750A4.toInt()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建分类" else "编辑分类") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 名称输入
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationError = null
                    },
                    label = { Text("名称") },
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.size(16.dp))

                // 颜色选择
                Text("颜色", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                ColorPickerRow(
                    selected = color,
                    onSelect = { color = it },
                )

                if (errorMessage != null) {
                    Spacer(Modifier.size(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isBlank()) {
                        validationError = "名称不能为空"
                        return@Button
                    }
                    // 重名校验：新建/编辑都查（编辑时排除自身）
                    if (existingNames.contains(trimmedName) &&
                        trimmedName != initial?.name
                    ) {
                        validationError = "已存在同名分类"
                        return@Button
                    }
                    val newCategory = EventCategory(
                        id = initial?.id ?: 0L,
                        name = trimmedName,
                        color = color,
                        iconKey = initial?.iconKey ?: "Label",
                        sortOrder = initial?.sortOrder ?: 50,
                        isDefault = initial?.isDefault ?: false,
                    )
                    onConfirm(newCategory)
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 颜色选择器（8 个固定 Material 3 调色板色）
 */
@Composable
private fun ColorPickerRow(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = remember {
        listOf(
            0xFF1976D2.toInt(),  // 蓝
            0xFF388E3C.toInt(),  // 绿
            0xFFE91E63.toInt(),  // 粉
            0xFFE53935.toInt(),  // 红
            0xFF00897B.toInt(),  // 青
            0xFF8E24AA.toInt(),  // 紫
            0xFFFF6F00.toInt(),  // 橙
            0xFF6750A4.toInt(),  // 主色
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { c ->
            val isSelected = c == selected
            OutlinedButton(
                onClick = { onSelect(c) },
                contentPadding = PaddingValues(0.dp),
                shape = CircleShape,
                modifier = Modifier
                    .size(40.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = Color(c),
                        shape = CircleShape,
                        modifier = Modifier.size(if (isSelected) 32.dp else 28.dp),
                    ) {}
                }
            }
        }
    }
}
