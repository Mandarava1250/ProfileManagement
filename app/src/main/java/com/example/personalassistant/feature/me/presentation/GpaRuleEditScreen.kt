package com.example.personalassistant.feature.me.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.feature.me.domain.model.GpaRule
import com.example.personalassistant.feature.me.domain.model.GradeMapping

/**
 * GPA 规则编辑页
 *
 * 字段：scaleMax / scaleMin / gradingScale（百分制→绩点）
 * 保存：调 [MeViewModel.updateGpaRule]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpaRuleEditScreen(
    onBack: () -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var draft by remember(state.gpaRule) { mutableStateOf(state.gpaRule) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPA 规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            try {
                                viewModel.updateGpaRule(draft)
                                errorText = null
                                onBack()
                            } catch (e: IllegalArgumentException) {
                                errorText = e.message ?: "规则非法"
                            }
                        },
                    ) { Text("保存") }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "说明：换算表是降序的百分制分数段，每段映射一个绩点。低于最低段落的分数按 scaleMin 计算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = draft.scaleMax.toString(),
                onValueChange = { text ->
                    text.toDoubleOrNull()?.let { draft = draft.copy(scaleMax = it) }
                },
                label = { Text("最高绩点（如 4.0 / 4.3 / 5.0）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.scaleMin.toString(),
                onValueChange = { text ->
                    text.toDoubleOrNull()?.let { draft = draft.copy(scaleMin = it) }
                },
                label = { Text("最低绩点（不及格线对应）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Text("百分制 → 绩点 转换表", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            // 按 minScore 升序展示（编辑时按降序逻辑）
            val sorted = draft.gradingScale.sortedBy { it.minScore }
            sorted.forEachIndexed { idx, mapping ->
                GradeMappingRow(
                    mapping = mapping,
                    onMinScoreChange = { v ->
                        val newList = draft.gradingScale.toMutableList()
                        newList[draft.gradingScale.indexOf(mapping)] = mapping.copy(minScore = v)
                        draft = draft.copy(gradingScale = newList)
                    },
                    onGradePointChange = { v ->
                        val newList = draft.gradingScale.toMutableList()
                        newList[draft.gradingScale.indexOf(mapping)] = mapping.copy(gradePoint = v)
                        draft = draft.copy(gradingScale = newList)
                    },
                    onDelete = {
                        val newList = draft.gradingScale.toMutableList()
                        newList.remove(mapping)
                        draft = draft.copy(gradingScale = newList)
                    },
                )
            }

            OutlinedButton(
                onClick = {
                    val newMapping = GradeMapping(
                        minScore = (draft.gradingScale.minOfOrNull { it.minScore } ?: 60) - 10,
                        gradePoint = draft.scaleMin,
                    )
                    draft = draft.copy(gradingScale = draft.gradingScale + newMapping)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("新增一段")
            }

            Spacer(Modifier.height(8.dp))
            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun GradeMappingRow(
    mapping: GradeMapping,
    onMinScoreChange: (Int) -> Unit,
    onGradePointChange: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = mapping.minScore.toString(),
                onValueChange = { it.toIntOrNull()?.let(onMinScoreChange) },
                label = { Text("≥分数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(end = 4.dp),
            )
            OutlinedTextField(
                value = mapping.gradePoint.toString(),
                onValueChange = { it.toDoubleOrNull()?.let(onGradePointChange) },
                label = { Text("绩点") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).padding(end = 4.dp),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}
