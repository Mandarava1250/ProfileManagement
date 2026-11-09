package com.example.personalassistant.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.feature.task.domain.model.ChecklistDefaults
import com.example.personalassistant.feature.task.domain.model.TaskListWithCount
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.presentation.ChecklistDefaultsViewModel

/**
 * Phase 22.5：清单默认设置
 *
 * 「新建清单项时自动填什么」—— 默认清单 / 默认优先级 / 默认提醒。
 *
 * 放在设置页而不是清单页，是因为这是一次性的长期偏好，
 * 不是「现在正在看哪个清单」这种会话内的临时状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistSection(
    lists: List<TaskListWithCount> = emptyList(),
    modifier: Modifier = Modifier,
    viewModel: ChecklistDefaultsViewModel = hiltViewModel(),
) {
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()

    // 草稿：等 defaults 首次到达后再填，之后不被回写覆盖
    var draft by remember { mutableStateOf(ChecklistDefaults()) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(defaults) {
        if (!seeded) {
            draft = defaults
            seeded = true
        }
    }

    var listMenuExpanded by remember { mutableStateOf(false) }

    // 下拉里的选项：「跟着当前清单」+ 全部未归档清单
    val listOptions = remember(lists) {
        buildList {
            add(null to "跟着当前清单")
            lists.filter { !it.list.isArchived }.forEach { add(it.list.id to it.list.displayName) }
        }
    }
    val selectedListLabel = listOptions.firstOrNull { it.first == draft.defaultListId }?.second ?: "跟着当前清单"

    val changed = draft != defaults

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModuleHeader(
            icon = Icons.Rounded.Checklist,
            title = "清单",
            subtitle = "新建清单项时的默认值",
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("默认清单", style = MaterialTheme.typography.titleMedium)
                Spacer8()
                Text(
                    text = "新清单项默认落在哪个清单。「跟着当前清单」表示在哪儿建就建在哪儿。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer8()

                ExposedDropdownMenuBox(
                    expanded = listMenuExpanded,
                    onExpandedChange = { listMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedListLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("默认清单") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = listMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = listMenuExpanded,
                        onDismissRequest = { listMenuExpanded = false },
                    ) {
                        listOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    draft = draft.copy(defaultListId = id)
                                    listMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer8()
                Text("默认优先级", style = MaterialTheme.typography.titleMedium)
                Spacer8()
                Text(
                    text = "新建清单项时自动带上的优先级，之后还能改。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer8()

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { priority ->
                        FilterChip(
                            selected = draft.defaultPriority == priority,
                            onClick = { draft = draft.copy(defaultPriority = priority) },
                            label = { Text(priority.label) },
                        )
                    }
                }

                Spacer8()
                Text("默认提醒", style = MaterialTheme.typography.titleMedium)
                Spacer8()
                Text(
                    text = "有截止时间时，提前多久提醒一下。留空 = 不提醒。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer8()

                var reminderText by remember(draft.defaultReminderMinutesBefore) {
                    mutableStateOf(draft.defaultReminderMinutesBefore?.toString().orEmpty())
                }
                OutlinedTextField(
                    value = reminderText,
                    onValueChange = { text ->
                        reminderText = text
                        val minutes = text.trim().toIntOrNull()
                        draft = draft.copy(
                            defaultReminderMinutesBefore = minutes?.takeIf { it >= 0 },
                        )
                    },
                    label = { Text("提前提醒（分钟）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer8()

                Button(
                    onClick = { viewModel.save(draft) },
                    enabled = changed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (changed) "保存默认值（有改动）" else "保存默认值")
                }
            }
        }
    }
}
