package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.LocalAppSurfaces

/**
 * 分组入口行 —— Phase 26 方案的「我的」页 / 设置页语言。
 *
 * 纯文字行，不带图标、不带卡片。行与行之间一条细分隔线（[ThinDivider]），
 * 整组上下用留白（[SectionLabel]）切开。
 *
 * @param title 入口名
 * @param value 右侧当前值（如「静谧蓝」「2 个」）；null 则不画
 * @param icon 左侧图标槽（20dp），null 则不画
 * @param onClick 点击回调；null 则整行不可点
 */
@Composable
fun GroupEntryRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(start = 20.dp, end = 12.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
            Box(modifier = Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 细分隔线 —— 与 [LocalAppSurfaces] 的 divider 配套（暖白体系里的一条细线）。 */
@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = LocalAppSurfaces.current.divider,
    )
}
