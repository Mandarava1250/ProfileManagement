package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskViewMode

/**
 * 优先级 TabRow（Phase 8b' + Phase 10 修复）
 *
 * 6 个 Tab：「全部」+ P1/P2/P3/P4/未排期
 *
 * - 选中 Tab：粗体 + textColor = primary + Material 3 TabRow 默认下划线
 * - 每个 Tab 显示：别名 + 任务计数（"立即执行 (3)"）
 * - 长按 Tab 触发重命名（"全部" Tab 不可长按）
 *
 * Phase 10 修复：原 fixed `TabRow` 在窄屏手机（<400dp）上 6 个 Tab 等分宽度，
 * 每个 Tab 约 60dp 宽，但 4 字别名（"立即执行"/"计划安排"/"委托/简化"/"未排期"）
 * 单行放不下，被 Ellipsis 截成 "立..."/"计..."/"委..."/"未..."；
 * TabLabel 内部的 Row 没有宽度约束，"消除"(0)" 被换行成两行，呈现出
 * 「清除()」这种上下错位的排版。改为 [ScrollableTabRow] 让 6 个 Tab 按 intrinsic
 * 宽度排列 + 横向滚动可访问，Tab 文本不再被截断；并给 TabLabel 内部两个 Text
 * 加上 maxLines=1 + softWrap=false，强制不换行。
 *
 * Phase 8final G2：**矩阵视图下高亮「全部」**。
 * 矩阵与 ALL 是同一份数据（见 [com.example.personalassistant.feature.task.domain.model.TaskMatrix.from]），
 * 所以 [TaskViewMode.MATRIX] 在 TabRow 里等同于「全部」被选中；
 * 布局切换由 [MatrixViewToggle] 单独负责，不抢这个 TabRow 的语义。
 * 点任意分组 Tab 会走 onSelectQuadrant → 切到 BY_QUADRANT，等效退出矩阵。
 *
 * 实现注意：Material 3 `Tab` 的 onClick 与 onLongClick 不冲突；
 * 这里用 [pointerInput] + [detectTapGestures] 在 Tab 外层包一层 Box 来捕获长按。
 */
@Composable
fun PriorityTabRow(
    displayPreferences: TaskDisplayPreferences,
    activeQuadrantCount: Map<TaskQuadrant, Int>,
    onSelectAll: () -> Unit,
    onSelectQuadrant: (TaskQuadrant) -> Unit,
    onLongPressQuadrant: (TaskQuadrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewMode = displayPreferences.viewMode
    val selectedQuadrant = displayPreferences.selectedQuadrant
    // Phase 8final G2：MATRIX 与 ALL 共用同一份数据，视觉上一律高亮「全部」
    val isAllSelected = viewMode == TaskViewMode.ALL || viewMode == TaskViewMode.MATRIX
    val totalCount = activeQuadrantCount.values.sum()

    // BY_QUADRANT 模式下若 selectedQuadrant 为 null，兜底 P1
    val selectedIndex: Int = if (isAllSelected) {
        0
    } else {
        (selectedQuadrant ?: TaskQuadrant.P1).ordinal + 1
    }

    // Phase 10：fixed TabRow → ScrollableTabRow
    // 6 个 Tab 在 360~400dp 宽屏上根本塞不下；改滚动后按 intrinsic 宽度排，
    // 文字不再被 Ellipsis 截成 "立..."/"计..."/"委..."/"未..."。
    // edgePadding=0 让第 0 个 Tab 贴左边缘、与上方 TaskFilterBar / TagFilterBar 对齐，
    // 视觉上不会被左侧空白"挤"到中间。
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 0.dp,
    ) {
        // ===== Tab 0：全部（不可长按）=====
        Tab(
            selected = isAllSelected,
            onClick = onSelectAll,
            text = { TabLabel(alias = "全部", count = totalCount, isSelected = isAllSelected) },
        )

        // ===== Tab 1~5：5 个象限（可长按重命名）=====
        TaskQuadrant.entries.forEach { quadrant ->
            val isSelected = !isAllSelected && selectedQuadrant == quadrant
            val alias = TaskDisplayPreferences.aliasOf(displayPreferences, quadrant)
            val count = activeQuadrantCount[quadrant] ?: 0

            Box(
                modifier = Modifier
                    .pointerInput(quadrant) {
                        detectTapGestures(
                            onLongPress = { onLongPressQuadrant(quadrant) },
                        )
                    },
            ) {
                Tab(
                    selected = isSelected,
                    onClick = { onSelectQuadrant(quadrant) },
                    text = { TabLabel(alias = alias, count = count, isSelected = isSelected) },
                )
            }
        }
    }
}

/**
 * Tab 内部统一的「名字 + 计数」格式（抽出避免重复代码）。
 *
 * Phase 10 修复：原来的 Row 没设宽度约束，子 Text 用默认 wrap，
 * 窄 Tab 宽度下会把 "(0)" 挤到下一行出现 "消除" / "(0)" 上下错位。
 *
 * Phase 10b 修复：之前给别名 Text 加了 `Modifier.weight(1f, fill = false)` 想让
 * 别名占据剩余空间，但 TabLabel 外层是 `wrapContentWidth()` —— weight 只在父
 * Row 宽度确定（fillMaxWidth / 固定宽度）时才有意义，wrapContent 下没有"剩余
 * 空间"可分，结果 fill=false 在 0 剩余下测成 0 宽度，别名文字直接消失（只剩
 * "(0)" 这种 wrapContent 的兄弟组件能显示）。修复：去掉 weight，让别名 Text
 * 用 intrinsic 测宽；ScrollableTabRow 本身就是 wrap content 的 Tab 容器，
 * Tab 宽 = alias 宽 + spacer + count 宽，整体单行显示，超出屏幕宽度时可
 * 横滚，无需再用 weight。
 *
 * - 外层 Row 宽度跟随内容（wrapContentWidth）
 * - 别名 Text 用 intrinsic 测宽 + softWrap=false + 单行 Ellipsis
 * - 计数 Text 用 wrapContentWidth + softWrap=false，强制单行
 * - 两者之间 4dp 间距，整体在 ScrollableTabRow 里按 intrinsic 排，超出可横滚
 */
@Composable
private fun TabLabel(alias: String, count: Int, isSelected: Boolean) {
    Row(
        modifier = Modifier
            .wrapContentWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = alias,
            style = if (isSelected) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.wrapContentWidth(),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.wrapContentWidth(),
        )
    }
}