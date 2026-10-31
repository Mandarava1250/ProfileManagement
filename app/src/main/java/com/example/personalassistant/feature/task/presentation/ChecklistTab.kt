package com.example.personalassistant.feature.task.presentation

import androidx.compose.foundation.layout.Arrangement
import com.example.personalassistant.core.ui.components.EmptyState
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.R
import com.example.personalassistant.feature.task.presentation.components.ChecklistItem

/**
 * 清单 Tab 内容：顶部输入框 + 独立清单项列表
 */
@Composable
fun ChecklistTab(
    state: ChecklistUiState,
    onToggleComplete: (Long, Boolean) -> Unit,
    onDelete: (SubTask) -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val canSubmit = input.isNotBlank()

    // Phase 10 修复：把提交逻辑抽出来，IconButton 点击 + 软键盘「完成」共用同一条路径，
    // 保证两条入口行为一致（trim 校验、清空输入框、调 onAdd）。每次重组重建 lambda
    // 是 OK 的 —— 重建只是 OnClick 的引用更新，Compose 会按需重组 IconButton，逻辑等价。
    val submit: () -> Unit = {
        val title = input.trim()
        if (title.isNotEmpty()) {
            onAdd(title)
            input = ""
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("添加清单项…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                // Phase 10 修复：让软键盘右下角变成「完成」，按回车真的能提交。
                // 之前没接 imeAction，singleLine=true 时回车只是被吞掉，提示语
                // 「回车或点击 + 即可添加」只兑现了一半，用户以为 + 没反应。
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit() },
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                // Phase 10 修复：保留 enabled 守卫避免提交空项，但 disabled 时显式
                // 给 Icon 一个 outline 灰色并加透明度，让「+ 不可点」这件事一眼可见。
                // 之前 enabled=false 时 IconButton 自动降 alpha 在某些主题下不明显，
                // 用户空着输入框点 + 看不到任何反馈，以为按钮坏了。
                onClick = submit,
                enabled = canSubmit,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "添加",
                    tint = if (canSubmit) {
                        LocalContentColor.current
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    },
                )
            }
        }

        HorizontalDivider()

        if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    illustration = R.drawable.ic_empty_checklist,
                    title = stringResource(R.string.empty_checklist_title),
                    description = stringResource(R.string.empty_checklist_desc),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { item ->
                    ChecklistItem(
                        item = item,
                        onToggleComplete = { onToggleComplete(item.id, it) },
                        onDelete = { onDelete(item) },
                    )
                }
                item(key = "bottom_spacer") {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}