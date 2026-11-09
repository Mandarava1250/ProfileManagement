package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.LocalAppSurfaces

/**
 * 下划线标签页 —— Phase 26 方案的切换控件。
 *
 * 和记账页的「流水 / 报表 / 零钱池 / 预算」、课表页的「时间线 / 整周网格」是同一套语言：
 * 选中项加粗 + 2dp 品牌蓝下划线，未选中项走 `onSurfaceVariant`。
 *
 * 不用 M3 的 `TabRow`：它是「胶囊指示器」样式（带一个圆角滑块底），
 * 跟这套方案「色只做标识、不铺色块」的原则冲突。
 *
 * @param tabs 标签文案
 * @param selectedIndex 当前选中项下标
 * @param onSelect 点击回调，参数是下标
 */
@Composable
fun UnderlineTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = 20.dp)) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        // 等宽铺满：每个标签占 1/N。
                        // 不用 fillMaxWidth 的下划线槽 —— 那样第一个标签会把
                        // Row 的剩余空间全吃掉，后面的标签被挤出屏幕；
                        // 也不能用 IntrinsicSize.Min —— 跟 fillMaxWidth 撞上会无限测量。
                        .weight(1f)
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(index) },
                            role = Role.Tab,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(8.dp))
                    // 每个标签都留一条 2dp 槽位，未选中的是透明的 ——
                    // 这样切换时下划线不会让整排标签的基线跳一下
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                    )
                }
                if (index != tabs.lastIndex) {
                    Spacer(Modifier.width(20.dp))
                }
            }
        }
        HorizontalDivider(color = LocalAppSurfaces.current.divider)
    }
}
