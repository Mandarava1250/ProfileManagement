package com.example.personalassistant.feature.settings.appearance

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.core.ui.components.AppCard
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.feature.settings.theme.BackgroundStyle
import com.example.personalassistant.feature.settings.theme.ThemeMode
import com.example.personalassistant.feature.settings.theme.ThemeViewModel

/**
 * 外观设置（Phase 14）
 *
 * 入口：「我」页 → 外观。
 *
 * 三件事：
 * - **主题模式**：跟随系统 / 浅色 / 深色
 * - **主题色**：8 个预设（默认蓝在第一个），挑完立刻生效
 * - **背景**：5 个浅色底预设（深色模式下不生效 —— 深底再叠浅色会晃眼）
 *
 * 改动都是即时生效的：写进 DataStore → [ThemeViewModel] 发新值 →
 * MainActivity 顶层重新套色板。没有「保存」按钮 —— 颜色这种东西要边看边调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val prefs by viewModel.themePreferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            // ===== 主题模式 =====
            SectionTitle("主题模式")
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                    FilterChip(
                        selected = prefs.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(themeModeLabel(mode)) },
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.xs))

            // ===== 主题色 =====
            SectionTitle("主题色")
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.md),
            ) {
                ColorGrid(
                    colors = PRESET_PRIMARY_COLORS,
                    selected = prefs.primaryColor,
                    onSelect = { viewModel.setPrimaryColor(it) },
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = "点「默认蓝」可以恢复品牌色",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { viewModel.setPrimaryColor(null) },
                )
            }

            Spacer(Modifier.height(AppSpacing.xs))

            // ===== 背景 =====
            SectionTitle("背景")
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.md),
            ) {
                BackgroundGrid(
                    selected = prefs.backgroundStyle,
                    onSelect = { viewModel.setBackgroundStyle(it) },
                )
            }

            // ===== 动态色（Android 12+）=====
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(AppSpacing.xs))
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("壁纸取色", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "用系统壁纸配色替换主题色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefs.dynamicColor,
                            onCheckedChange = { enabled ->
                                viewModel.setDynamicColor(enabled)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = AppSpacing.xs, bottom = 2.dp),
    )
}

/**
 * 主题色网格：4 列
 */
@Composable
private fun ColorGrid(
    colors: List<Color>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        colors.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                row.forEach { color ->
                    val argb = color.toArgb()
                    ColorSwatch(
                        color = color,
                        selected = selected == argb,
                        onClick = { onSelect(argb) },
                    )
                }
            }
        }
    }
}

/**
 * 背景网格：5 个底色
 */
@Composable
private fun BackgroundGrid(
    selected: BackgroundStyle,
    onSelect: (BackgroundStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        BackgroundStyle.entries.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                row.forEach { style ->
                    val bg = Color(style.lightBackground)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .border(
                                width = if (style == selected) 3.dp else 1.dp,
                                color = if (style == selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable { onSelect(style) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (style == selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = onColorFor(color),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    else -> "跟随系统"
}

/** 预设主题色：第一个是品牌默认蓝 */
private val PRESET_PRIMARY_COLORS = listOf(
    Color(0xFF0B57D0), // 默认蓝
    Color(0xFF3F51B5), // 靛
    Color(0xFF00838F), // 青
    Color(0xFF2E7D32), // 翠绿
    Color(0xFFF57F17), // 琥珀
    Color(0xFFC62828), // 砖红
    Color(0xFFAD1457), // 玫紫
    Color(0xFF37474F), // 墨
)