package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppSpacing

/**
 * Phase 18.6：多选模式下的**批量操作条**。
 *
 * 出现在清单页顶部（输入条下面），长按任意清单项进入多选后显示。
 * 一排图标按钮：改日期 / 改优先级 / 移入清单 / 加标签 / 删除，末尾一个「取消」退出多选。
 *
 * 按钮用图标 + 无障碍描述，不塞文字 —— 五个动作横排放不下，文字会把条撑得很宽。
 * 选了几项在左侧用「已选 N 项」说明。
 */
@Composable
internal fun ChecklistBatchBar(
    selectedCount: Int,
    onChangeDate: () -> Unit,
    onChangePriority: () -> Unit,
    onMoveToList: () -> Unit,
    onAttachTag: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Text(
            text = "已选 $selectedCount 项",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        BatchAction(Icons.Rounded.DateRange, "改日期", onChangeDate)
        BatchAction(Icons.Rounded.Flag, "改优先级", onChangePriority)
        BatchAction(Icons.Rounded.List, "移入清单", onMoveToList)
        BatchAction(Icons.Rounded.LocalOffer, "加标签", onAttachTag)
        BatchAction(
            icon = Icons.Rounded.Delete,
            contentDescription = "删除",
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error,
        )
        BatchAction(Icons.Rounded.Close, "取消多选", onCancel)
    }
}

/** 批量操作条上的一个图标按钮。 */
@Composable
private fun BatchAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick, modifier = Modifier.padding(0.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
