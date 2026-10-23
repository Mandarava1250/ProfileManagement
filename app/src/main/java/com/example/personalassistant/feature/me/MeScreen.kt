package com.example.personalassistant.feature.me

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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.personalassistant.feature.me.domain.usecase.GpaReverseResult
import com.example.personalassistant.feature.me.presentation.MeViewModel
import com.example.personalassistant.feature.me.presentation.MeUiState
import com.example.personalassistant.navigation.Screen

/**
 * 「我」页（Phase 12）
 *
 * 内容：
 *  1. GPA 概览（已修 / 总学分 / 课程数；目标 GPA 输入框）
 *  2. GF 反推结果卡（依据 GpaRule）
 *  3. 个人陈述（多行文本，自动保存）
 *  4. 右上角 ⚙️ 设置 / 🎚 GPA 规则（用户自定义）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    navController: NavController,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 右上角：GPA 规则编辑入口（伙伴要求）
                    //
                    // Phase 13：原来的 ⚙️ 设置入口已移除 —— 设置现在是底部导航的第 6 个 Tab，
                    // 「我」页再放一个入口只会让人以为两处设置的东西不一样。
                    IconButton(onClick = { navController.navigate(Screen.EditGpaRule.route) }) {
                        Icon(Icons.Filled.Tune, contentDescription = "GPA 规则")
                    }
                },
            )
        }
    ) { padding ->
        MeContent(
            padding = padding,
            state = state,
            onGpaTargetTextChange = viewModel::updateGpaTarget,
            onResumeChange = viewModel::updateResumeText,
        )
    }
}

@Composable
private fun MeContent(
    padding: PaddingValues,
    state: MeUiState,
    onGpaTargetTextChange: (Double) -> Unit,
    onResumeChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // ===== GPA 概览 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = state.gpaSummary?.let { "%.2f".format(it.gpa) } ?: "暂无成绩",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "目标 ${"%.2f".format(state.gpaTarget)} / ${"%.1f".format(state.gpaRule.scaleMax)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                val sub = state.gpaSummary?.let {
                    "已修 ${it.courseCount} 门 · 总学分 ${"%.1f".format(it.totalCredits)}" +
                        (state.currentSemesterName?.let { name -> " · 已排除 $name" } ?: "")
                } ?: "在课程里填入学分和成绩后会自动计算"
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ===== 目标 GPA 输入框 =====
        Text("目标 GPA", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        GpaTargetTextField(
            target = state.gpaTarget,
            scaleMax = state.gpaRule.scaleMax,
            onTargetChange = onGpaTargetTextChange,
        )

        Spacer(Modifier.height(12.dp))

        // ===== GF 反推结果卡 =====
        ReverseResultCard(result = state.reverseResult, hasGpa = state.gpaSummary != null)

        Spacer(Modifier.height(20.dp))

        // ===== 个人陈述 =====
        Text("个人陈述 / 简历", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.resumeText,
            onValueChange = onResumeChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            placeholder = { Text("写点什么……比如目标、兴趣、特长、待办都行") },
        )

        Spacer(Modifier.height(24.dp))

        // Phase 13：这里原来有一张「设置」入口卡片（主题 · 备份 · 课程表设置 · 通知）。
        // 设置已升级为底部导航的独立 Tab，入口卡片随之移除。
    }
}

/**
 * 目标 GPA 输入框：数字键盘 + 解析后回写
 *
 * 设计：本地维护 text 字符串以容忍"3."这种半成品；onValueChange 只把解析成功的 Double 回调
 */
@Composable
private fun GpaTargetTextField(
    target: Double,
    scaleMax: Double,
    onTargetChange: (Double) -> Unit,
) {
    var text by remember(target) { mutableStateOf(formatGpaText(target)) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let { onTargetChange(it) }
        },
        label = { Text("目标 GPA（0.0 - ${"%.1f".format(scaleMax)}）") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatGpaText(v: Double): String {
    // 4.0 写成 "4"，4.50 写成 "4.5"，避免无意义零
    return if (v == v.toLong().toDouble()) v.toLong().toString()
    else "%.2f".format(v).trimEnd('0').trimEnd('.')
}

/**
 * GF 反推结果卡：按 [GpaReverseResult] 5 条规则给文案
 */
@Composable
private fun ReverseResultCard(result: GpaReverseResult?, hasGpa: Boolean) {
    val (text, color) = when {
        !hasGpa -> "填入课程学分后自动反推" to MaterialTheme.colorScheme.onSurfaceVariant
        result == null -> "暂无数据" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> when (result) {
            is GpaReverseResult.Achieved ->
                "目标可达：未来课程平均需要 ≥ ${"%.2f".format(result.gpa)}" to MaterialTheme.colorScheme.primary
            is GpaReverseResult.Trivial ->
                "目标在给定学分内很容易达到（所需 ${"%.2f".format(result.requiredGpa)} < 最低 ${"%.2f".format(result.minRequired)}）" to MaterialTheme.colorScheme.tertiary
            is GpaReverseResult.Impossible ->
                "在给定学分内目标不可达（需要 ${"%.2f".format(result.requiredGpa)} > 最高 ${"%.2f".format(result.maxAchievable)}）" to MaterialTheme.colorScheme.error
            GpaReverseResult.NoFutureCredits ->
                "无法反推：未来纳入绩点学分为 0" to MaterialTheme.colorScheme.onSurfaceVariant
            is GpaReverseResult.InvalidInput ->
                "输入数据非法：${result.reason}" to MaterialTheme.colorScheme.error
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GF 反推（按 GpaRule）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
    }
}
