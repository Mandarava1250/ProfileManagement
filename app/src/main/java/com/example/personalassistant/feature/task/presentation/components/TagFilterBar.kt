package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.Tag

/**
 * Phase 6c：任务列表顶部 TagFilterBar（标签云 chip 行）。
 *
 * - 数据源：标签云（频次降序），由 [TaskViewModel.uiState.tagsWithCount] 提供
 * - 交互：
 *   - 点击「全部」chip → [onClear]（清空 tag 筛选）
 *   - 点击某个 tag chip → [onTagToggle] (tagId)（切换该 tag 筛选）
 * - chip 数量无限 → 横向滚动
 *
 * 与 [TaskFilterBar] 互不耦合：TaskFilterBar 管分类（study/life/...），
 * TagFilterBar 管标签（work/urgent/...）。两者筛选结果通过 ViewModel
 * 内部组合（And(ByTag, ByCategory)）。
 */
@Composable
fun TagFilterBar(
    tags: List<Tag>,
    selectedTagId: Long?,
    onTagToggle: (Long) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) {
        // 没有标签时不显示（避免空行噪音）
        return
    }
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 第一 chip：「全部」—— 清空 tag 筛选
        ClearChip(
            selected = selectedTagId == null,
            onClick = onClear,
        )

        tags.forEach { tag ->
            TagChip(
                tag = tag,
                selected = tag.id == selectedTagId,
                onClick = { onTagToggle(tag.id) },
            )
        }
    }
}

/**
 * 「全部」chip：未选中状态表示不过滤 tag。
 *
 * 与 [TagChip] 不同的视觉：始终用主题色（primary），仅靠透明度区分选中/未选中。
 */
@Composable
private fun ClearChip(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            // Phase 14：热区补到 48dp —— 小 chip 手指不好按
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // Phase 14：分类行已经有一个「全部」了 —— 这里改「不限」，两个「全部」会看花眼
                text = "不限",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}