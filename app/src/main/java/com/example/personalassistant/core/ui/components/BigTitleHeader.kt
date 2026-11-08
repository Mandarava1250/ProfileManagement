package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppBigTitle
import com.example.personalassistant.core.ui.theme.AppPageSubtitle

/**
 * 页面大标题 —— Phase 26「秩序 / 留白」方案的标题语言。
 *
 * ## 为什么不用 AppTopBar
 *
 * 现状的顶栏有三种长相：清单页是纯 `primaryContainer`、课表页是品牌渐变、习惯页是裸
 * `TopAppBar`（见 `TaskScreen.kt:183` / `TimetableScreen.kt:101` / `HabitScreen.kt:74`）。
 * 根因是 M3 的 `TopAppBar` 高度固定 64dp、必须自带底色，而这套方案要的是
 * 「标题 + 副标题 + 右侧动作」的大标题区 —— 用 `TopAppBar` 塞进去必然变形。
 *
 * 所以这里自绘标题区：26sp 大标题 + 12sp 副标题 + 右侧 44dp 圆形动作按钮。
 *
 * @param title 主标题，26sp
 * @param subtitle 副标题（日期 / 统计口径），12sp；null 则不画
 * @param navigationIcon 左侧导航位（44dp 圆形按钮）
 * @param actions 右侧操作区，每个动作自己保证 ≥ 44dp 触达
 */
@Composable
fun BigTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            navigationIcon?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppBigTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = AppPageSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            actions()
        }
    }
}
