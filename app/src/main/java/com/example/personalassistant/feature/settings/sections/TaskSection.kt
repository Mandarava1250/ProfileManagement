package com.example.personalassistant.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.presentation.TaskSettingsViewModel

/**
 * Phase 13：任务分组的长期设置
 *
 * 任务侧只放一样东西：**象限别名**。
 *
 * 任务页顶部那个「列表 / 矩阵」切换器不算设置项 —— 它切的是"现在正在看哪种视图"，
 * 属于会话内的临时选择；而象限别名会持久化下来，是长期偏好。
 */
@Composable
fun TaskSection(
    modifier: Modifier = Modifier,
    viewModel: TaskSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    // 草稿：等 preferences 首次到达后再填，之后不再被回写覆盖（否则用户正在输入的会被冲掉）
    var drafts by remember { mutableStateOf<Map<TaskQuadrant, String>>(emptyMap()) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(preferences) {
        if (!seeded) {
            drafts = TaskQuadrant.entries.associateWith {
                TaskDisplayPreferences.aliasOf(preferences, it)
            }
            seeded = true
        }
    }

    // 有改动（且非空）的象限
    val changed = TaskQuadrant.entries.filter { quadrant ->
        val draft = drafts[quadrant]?.trim().orEmpty()
        draft.isNotEmpty() && draft != TaskDisplayPreferences.aliasOf(preferences, quadrant)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModuleHeader(
            icon = Icons.Rounded.Checklist,
            title = "任务",
            subtitle = "四象限的显示名称",
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("象限别名", style = MaterialTheme.typography.titleMedium)
                Spacer8()
                Text(
                    text = "给每个象限起个你顺口的称呼，任务页的 Tab 会跟着变。" +
                        "只改显示名，不改象限本身的判定规则。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer8()

                TaskQuadrant.entries.forEach { quadrant ->
                    OutlinedTextField(
                        value = drafts[quadrant].orEmpty(),
                        onValueChange = { text ->
                            drafts = drafts + (quadrant to text)
                        },
                        label = { Text(quadrant.label) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }

                Spacer8()

                Button(
                    onClick = {
                        changed.forEach { quadrant ->
                            viewModel.setAlias(quadrant, drafts[quadrant].orEmpty())
                        }
                    },
                    enabled = changed.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (changed.isEmpty()) "保存别名" else "保存别名（${changed.size} 项改动）"
                    )
                }
            }
        }
    }
}
