package com.example.personalassistant.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.personalassistant.core.ui.components.BigTitleHeader
import com.example.personalassistant.core.ui.components.GroupEntryRow
import com.example.personalassistant.core.ui.components.SectionLabel
import com.example.personalassistant.core.ui.components.ThinDivider
import com.example.personalassistant.core.ui.components.ThinProgressBar
import com.example.personalassistant.core.ui.theme.LocalAppSurfaces
import com.example.personalassistant.feature.me.domain.usecase.GpaReverseResult
import com.example.personalassistant.feature.me.presentation.MeUiState
import com.example.personalassistant.feature.me.presentation.MeViewModel
import com.example.personalassistant.feature.profile.presentation.accountswitcher.AccountSwitcherViewModel
import com.example.personalassistant.feature.settings.theme.ThemeMode
import com.example.personalassistant.feature.settings.theme.ThemeViewModel
import com.example.personalassistant.navigation.Screen

/**
 * 主题模式的中文名。
 *
 * ThemeMode 本身只有存储用的 key，中文名留在 UI 层 ——
 * 枚举是数据模型，不该知道界面上的字怎么写。
 */
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    else -> "跟随系统"
}

/**
 * 「我」页（Phase 12 → Phase 26 重画）
 *
 * 内容：
 * 1. **档案大卡**：64dp 头像 + 名字 + 账户名副行
 * 2. **GPA 区块**：38sp 细字重大数字 + 3dp 进度条 + 底部左右两段小字
 * 3. **学业**：目标 GPA 输入 + GF 反推
 * 4. **外观 / 数据与账户 / 关于**：三组纯文字入口行
 * 5. **个人陈述**
 *
 * ## 档案卡的数据从哪来
 *
 * 用的是**现有数据**：账户名 + 头像 URI，都来自 `UserProfileEntity`。
 * 这里刻意不给「个人档案」加新的数据库字段（要改 Entity + Room 迁移），
 * 也不做假的姓名/学号 —— 宁可只显示账户名，也不显示编出来的信息。
 * 头像没有就退化成「首字色块」。
 */
@Composable
fun MeScreen(
    navController: NavController,
    viewModel: MeViewModel = hiltViewModel(),
    // 账户清单（名字 + 头像）复用账户切换条的状态，不另起一条流
    accountSwitcherViewModel: AccountSwitcherViewModel = hiltViewModel(),
    // 外观组的右侧当前值（主题模式 / 主题色 / 背景）也复用同一份偏好
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accountState by accountSwitcherViewModel.state.collectAsStateWithLifecycle()
    val themePrefs by themeViewModel.themePreferences.collectAsStateWithLifecycle()

    val currentAccount = accountState.accounts.firstOrNull { it.id == accountState.currentAccountId }

    Scaffold(
        topBar = {
            BigTitleHeader(
                title = "我的",
                // Phase 14：「我」是二级页（从底栏「更多」进入），返回箭头是合理出口
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 搜索入口放在顶栏 —— 与清单页保持一致，不藏进「更多」里
                    IconButton(onClick = { navController.navigate(Screen.GlobalSearch.route) }) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                },
            )
        },
    ) { padding ->
        MeContent(
            padding = padding,
            state = state,
            accountName = currentAccount?.name.orEmpty(),
            avatarUri = currentAccount?.avatarUri,
            accountCount = accountState.accounts.size,
            themeModeLabel = themeModeLabel(themePrefs.themeMode),
            primaryColorLabel = if (themePrefs.primaryColor == null) "默认蓝" else "自定义",
            backgroundLabel = themePrefs.backgroundStyle.label,
            onGpaTargetTextChange = viewModel::updateGpaTarget,
            onResumeChange = viewModel::updateResumeText,
            onAppearanceClick = { navController.navigate(Screen.Appearance.route) },
            onGpaRuleClick = { navController.navigate(Screen.EditGpaRule.route) },
            onSettingsClick = { navController.navigate(Screen.Settings.route) },
        )
    }
}

@Composable
private fun MeContent(
    padding: PaddingValues,
    state: MeUiState,
    accountName: String,
    avatarUri: String?,
    accountCount: Int,
    themeModeLabel: String,
    primaryColorLabel: String,
    backgroundLabel: String,
    onGpaTargetTextChange: (Double) -> Unit,
    onResumeChange: (String) -> Unit,
    onAppearanceClick: () -> Unit,
    onGpaRuleClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        // ===== 档案大卡 =====
        ProfileCard(name = accountName, avatarUri = avatarUri)

        // ===== GPA 区块 =====
        GpaBlock(state = state)

        // ===== 学业 =====
        SectionLabel("学业")
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            GpaTargetTextField(
                target = state.gpaTarget,
                scaleMax = state.gpaRule.scaleMax,
                onTargetChange = onGpaTargetTextChange,
            )
            Spacer(Modifier.height(12.dp))
            GroupEntryRow(
                title = "GPA 规则",
                value = "${"%.1f".format(state.gpaRule.scaleMax)} 分制",
                onClick = onGpaRuleClick,
            )
            Spacer(Modifier.height(12.dp))
            ReverseResultCard(result = state.reverseResult, hasGpa = state.gpaSummary != null)
        }

        // ===== 外观 =====
        SectionLabel("外观")
        GroupEntryRow(title = "主题模式", value = themeModeLabel, onClick = onAppearanceClick)
        ThinDivider()
        GroupEntryRow(title = "主题色", value = primaryColorLabel, onClick = onAppearanceClick)
        ThinDivider()
        GroupEntryRow(title = "背景", value = backgroundLabel, onClick = onAppearanceClick)

        // ===== 数据与账户 =====
        SectionLabel("数据与账户")
        GroupEntryRow(title = "记账账户", value = "$accountCount 个", onClick = onSettingsClick)
        ThinDivider()
        GroupEntryRow(title = "备份与恢复", onClick = onSettingsClick)

        // ===== 关于 =====
        SectionLabel("关于")
        // 破坏性操作（清数据）留在设置页 —— 那边有完整的三阶段确认；
        // 这里只做入口，不重复实现清除流程，避免两处逻辑走偏。
        DangerEntryRow(title = "清除全部数据", onClick = onSettingsClick)

        // ===== 个人陈述 =====
        SectionLabel("个人陈述")
        OutlinedTextField(
            value = state.resumeText,
            onValueChange = onResumeChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(160.dp),
            placeholder = { Text("写点什么……比如目标、兴趣、特长、待办都行") },
        )
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * 档案大卡：64dp 圆形头像 + 名字 + 账户名副行。
 *
 * 没有头像时退化成「首字色块」：用户没设头像是常态，
 * 画一个灰色人形图标会让这一屏看起来像「资料没填」，而不是「这就是你」。
 */
@Composable
private fun ProfileCard(
    name: String,
    avatarUri: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(LocalAppSurfaces.current.paper),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUri.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                )
            } else {
                Text(
                    text = name.take(1).ifBlank { "·" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { "未命名账户" },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "记账账户",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * GPA 区块：38sp 细字重大数字 + 3dp 进度条 + 底部左右两段小字。
 *
 * 数字用 FontWeight.Light 是这套方案的「大数」语言 ——
 * 34px/38px 这种尺寸下，粗体数字会糊成一团黑。
 */
@Composable
private fun GpaBlock(
    state: MeUiState,
    modifier: Modifier = Modifier,
) {
    val summary = state.gpaSummary
    val gpa = summary?.gpa ?: 0.0
    val target = state.gpaTarget
    val progress = if (target > 0) (gpa / target).coerceIn(0.0, 1.0) else 0.0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = "当前 GPA",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = summary?.let { "%.2f".format(it.gpa) } ?: "—",
                fontSize = 38.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = " / ${"%.1f".format(target)}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }

        ThinProgressBar(
            progress = progress.toFloat(),
            modifier = Modifier.padding(top = 12.dp),
            height = 3.dp,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            Text(
                text = summary?.let {
                    "已修 ${"%.1f".format(it.totalCredits)} 学分 · ${it.courseCount} 门"
                } ?: "在课程里填入学分和成绩后会自动计算",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (summary == null) {
                    ""
                } else {
                    val diff = target - gpa
                    if (diff > 0.005) "距目标还差 ${"%.2f".format(diff)}" else "已达成目标"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 危险操作行 —— 与 [GroupEntryRow] 同一套排版，只是文字是红的。
 *
 * 之所以不复用 GroupEntryRow：它没有颜色槽，而破坏性操作必须是红的
 * （红色是这套方案里唯一的「危险」信号，不能省）。
 */
@Composable
private fun DangerEntryRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = LocalAppSurfaces.current.paper,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            )
            .padding(16.dp),
    ) {
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
