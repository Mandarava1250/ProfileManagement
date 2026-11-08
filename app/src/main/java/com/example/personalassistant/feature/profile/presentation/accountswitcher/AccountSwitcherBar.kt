package com.example.personalassistant.feature.profile.presentation.accountswitcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.personalassistant.core.ui.components.ColorBar
import com.example.personalassistant.core.ui.components.ensureBarContrast

/**
 * Phase 9b B2：记账页顶部的账户切换条 → Phase 26 C 化。
 *
 * ## 为什么从 Chip 换成文字行
 *
 * 原来选中态是给整个 Chip 铺一块 `primaryContainer` 色块 —— 一排账户就有好几种底色，
 * 和「色只做标识、不铺色块」的方案语言冲突。现在选中态只画一根 3dp 竖条，
 * 未选中的账户留同样的空位，账户名不会左右跳。
 *
 * ## 为什么是横向滑动
 *
 * 典型场景下账户数是 1-3 个，一行平铺就能一眼看全，且点击目标大、无需二次展开；
 * Dropdown 每次切换多一跳、也看不出「一共几个账本」。account count 很大时
 * `horizontalScroll` 兜住，不会横向溢出。
 *
 * ## 与 `AccountSettingsCard` 的关系
 *
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
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        state.accounts.forEach { account ->
            AccountLabel(
                account = account,
                selected = account.id == state.currentAccountId,
                enabled = !state.isSwitching,
                onClick = { onSwitch(account.id) },
            )
        }
    }
}

@Composable
private fun AccountLabel(
    account: AccountSwitcherItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 选中竖条：品牌色。
        // 用户可以自选主题色（applyAccent），浅色主色压在白底上等于没有 ——
        // 所以走 ensureBarContrast 压暗到 ≥ 3:1。
        ColorBar(
            color = if (selected) {
                ensureBarContrast(MaterialTheme.colorScheme.primary)
            } else {
                Color.Transparent
            },
            modifier = Modifier.height(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        AccountChipAvatar(account)
        Spacer(Modifier.width(6.dp))
        Text(
            text = account.name,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
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
        BoxAvatarPlaceholder()
    }
}

/** 无头像时的占位圆（与有头像时同尺寸，保证一行高度一致） */
@Composable
private fun BoxAvatarPlaceholder() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}
