package com.example.personalassistant.feature.task.presentation.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.presentation.components.TagChip
import com.example.personalassistant.feature.task.presentation.detail.AddTagBottomSheet
import com.example.personalassistant.feature.task.presentation.util.TagPaletteSource

/**
 * Phase 6c：详情页 Tag section。
 *
 * - 顶部：Section 标题 + 「+ 添加」按钮
 * - 内容：已选标签 chips（selected 态，点击 = 卸下回调）
 * - 底部：AddTagBottomSheet（按需弹出）
 *
 * 设计动机：
 * - 弹层是否可见由 section 自己管理（`var showAddSheet`），调用方不用关心
 * - ViewModel 暴露 addTagToTask / removeTagFromTask 回调，section 只负责渲染和事件转发
 */
@Composable
fun TagSection(
    tags: List<Tag>,
    paletteSource: TagPaletteSource,
    onRemoveTag: (Tag) -> Unit,
    onAddTag: (displayName: String, colorHex: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // ===== Section 标题 + 「+ 添加」按钮 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "标签",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${tags.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (tags.isEmpty()) {
                Text(
                    text = "还没有标签，点 + 添加一个 ♪",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                // chip 流式布局（横向滚动，标签数未来可能多）
                val scrollState = remember { androidx.compose.foundation.ScrollState(0) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        TagChip(
                            tag = tag,
                            selected = true,
                            onClick = { onRemoveTag(tag) },
                        )
                    }
                }
            }

            // ===== 「+ 添加」按钮 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AddTagButton(onClick = { showAddSheet = true })
            }
        }
    }

    AddTagBottomSheet(
        visible = showAddSheet,
        paletteSource = paletteSource,
        onDismiss = { showAddSheet = false },
        onCreateTag = { displayName, colorHex ->
            onAddTag(displayName, colorHex)
            // sheet 由 AddTagBottomSheet 内部 dismissSheet() 关闭
        },
    )
}

/**
 * 「+ 添加」小按钮（与 SubTaskSection 的 + 按钮风格一致）。
 */
@Composable
private fun AddTagButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "添加标签",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "添加",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}