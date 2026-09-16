package com.example.personalassistant.feature.profile.presentation.accountswitcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Phase 9b B2：记账页顶部的账户切换条。
 *
 * ## 为什么是 Chip 横向滑动（plan §4.2 的选项 A）
 * 典型场景下账户数是 1-3 个，Chip 全部平铺就能一眼看全，且点击目标大、无需二次展开；
 * Dropdown 每次切换多一跳、也看不出「一共几个账本」。account count 很大时
 * `horizontalScroll` 兜住，不会横向溢出。
 *
 * ## 为什么放在记账页内而不是 TopAppBar 的 title 里
 * `TopAppBar` 的 title 槽在窄屏（sw360dp）会被右侧 5 个 action 图标挤到只剩一个词宽，
 * 而账户名长度不可控（用户可自定义）。放进内容区首行则始终有整屏宽度可用。
 *
 * ## 与 `AccountSettingsCard` 的关系
 * 那张卡是管理入口（增删改），本组件只做切换。两者共用 `AccountRepository` 的数据，
 * 但**不共用 UI 状态** —— 这里连「新建账户」按钮都不放，避免用户在记账页误触管理动作。
 */
@Composable
fun AccountSwitcherBar(
    state: AccountSwitcherState,
    onSwitch: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 单账户 / 加载中整条隐藏：没有信息量的分组不该占首屏高度
    if (!state.isVisible) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.accounts.forEach { account ->
            AccountChip(
                account = account,
                selected = account.id == state.currentAccountId,
                enabled = !state.isSwitching,
                onClick = { onSwitch(account.id) },
            )
        }
    }
}

@Composable
private fun AccountChip(
    account: AccountSwitcherItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            AccountChipAvatar(account)
        },
        label = {
            Text(
                text = account.name,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        // ⚠️ 不要在这里手写 semantics { contentDescription = ... }。
        //
        // 试过：给选中 Chip 加 `contentDescription = "账户名，当前账户"`，想让 TalkBack 读出选中态。
        // 结果有两处问题（真机 UI test 直接失败暴露了第一点）：
        //   1. contentDescription 会**覆盖** Chip 的文本语义，`onNodeWithText("账户名")` 从此找不到它 ——
        //      所有基于文本的 UI test/无障碍工具都会突然失配。
        //   2. 它是冗余的：Material3 的 FilterChip 内部就带 `selectable(selected=...)`，
        //      TalkBack 本来就会念「已选中 / 未选中」，手写反而把「选中」这件事表达了两遍。
        // 结论：选中态靠组件自带语义 + 视觉（颜色 + 字重）即可，不要额外包一层。
    )
}

/**
 * Chip 内的小头像（18dp）。
 *
 * 复用 Coil（Phase 2 起就在项里，2.7.0，无需加依赖）；无头像时用 Person 占位，
 * 保证有/无头像两种 Chip 的高度一致 —— 否则同一行 Chip 会高低不齐。
 */
@Composable
private fun AccountChipAvatar(account: AccountSwitcherItem) {
    val hasAvatar = !account.avatarUri.isNullOrBlank()

    if (hasAvatar) {
        AsyncImage(
            model = account.avatarUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}