package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 全 App 统一的顶栏 —— Phase 14「樱花粉 2.0」视觉收口
 *
 * ## 为什么抽这个组件
 *
 * 顶栏散在十几个页面里，各写各的 `TopAppBar`，颜色、间距、高度全靠各处自己传，
 * 结果是「同一个 App，顶栏长得像五个 App」。品牌色在最显眼的位置反而最不统一。
 *
 * 这里把三件事收在一处：
 *
 * 1. **渐变背景**：从品牌色（当前主题 `primary`）淡出到背景色，
 *    浅色模式下品牌色一眼可见，深色模式下也不会刺眼
 * 2. **容器色透明**：M3 的 `TopAppBar` 默认铺 `surfaceContainer`，
 *    那会把渐变整个盖住 —— 所以这里把 `containerColor` 与 `scrolledContainerColor` 都设为透明
 * 3. **图标/文字颜色**：统一走 `onSurface` / `onSurfaceVariant`
 *
 * ## 渐变跟随主题色
 *
 * 渐变用的是 `MaterialTheme.colorScheme.primary`，不是写死的樱花粉。
 * 伙伴在「我 → 外观」里换主题色时，顶栏渐变会一起变 —— 写死颜色会打架。
 *
 * @param title 标题内容（一般传 `Text(stringResource(...))`）
 * @param navigationIcon 左侧导航位
 * @param actions 右侧操作区
 * @param scrollBehavior 滚动折叠行为，需要时传入
 * @param colors 需要覆盖时传；默认容器透明
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    ),
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface

    TopAppBar(
        title = title,
        modifier = modifier.background(
            Brush.horizontalGradient(
                colors = listOf(
                    primary.copy(alpha = 0.18f),
                    primary.copy(alpha = 0.06f),
                    surface,
                ),
            ),
        ),
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = colors,
    )
}
