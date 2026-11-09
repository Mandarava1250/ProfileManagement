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
 * 全 App 统一的顶栏 —— Phase 26「秩序 / 留白」方案
 *
 * ## 为什么抽这个组件
 *
 * 顶栏散在十几个页面里，各写各的 `TopAppBar`，颜色、间距、高度全靠各处自己传，
 * 结果是「同一个 App，顶栏长得像五个 App」。
 *
 * ## Phase 26 的改动：品牌渐变默认关闭
 *
 * 原来这里给顶栏铺一层 `primary → surface` 的横向渐变（`AppTopBar.kt:61-68`），
 * 但清单页自己又覆盖成不透明的 `primaryContainer`（`TaskScreen.kt:183`），
 * 习惯页则用裸 `TopAppBar`（`HabitScreen.kt:74`）—— 三个模块三种长相。
 *
 * 这套方案要的是「白底 + 大标题」，所以渐变**默认关闭**，
 * 需要时用 [showGradient] 显式打开（保留能力，不是删掉）。
 *
 * @param title 标题内容（一般传 `Text(stringResource(...))`）
 * @param navigationIcon 左侧导航位
 * @param actions 右侧操作区
 * @param scrollBehavior 滚动折叠行为，需要时传入
 * @param colors 需要覆盖时传；默认容器透明
 * @param showGradient 是否铺品牌渐变背景，默认 false（白底）
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
    showGradient: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface

    TopAppBar(
        title = title,
        modifier = modifier.then(
            if (showGradient) {
                Modifier.background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.18f),
                            primary.copy(alpha = 0.06f),
                            surface,
                        ),
                    ),
                )
            } else {
                Modifier
            }
        ),
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = colors,
    )
}
