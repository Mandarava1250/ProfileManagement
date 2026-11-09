package com.example.personalassistant.feature.habit.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Phase 26：习惯图标 key → ImageVector。
 *
 * ## 为什么自己带一份，而不复用记账的 IconRegistry
 *
 * 1. **语义不同**：记账那套 key 是「收支分类」（Restaurant / DirectionsBus …），
 *    习惯这套要的是「背单词 / 喝水 / 早睡」这种日常语义。
 * 2. **依赖方向**：habit 是叶子模块（只有 calendar 的日历同屏反过来读它的 domain），
 *    让它反向依赖 finance 的**表现层**会把两个 feature 绑死 ——
 *    finance 那边一旦挪动 IconRegistry，习惯页就编译不过。
 * 3. **兜底语义不同**：记账的 fallback 是通用的 Category 图标；
 *    习惯的默认是 Star（`habits.iconKey` 的历史默认值就是 "star"），
 *    用 Category 兜底会让老用户看到的图标突然全变了。
 *
 * ## 为什么必须兜底
 *
 * `iconKey` 是**用户数据**（存在 habits.iconKey 列里）。老数据、脏数据、
 * 未来加了新 key 但这一版 App 没跟上 —— 任何一种都不能让整页画不出来。
 * 所以未知 key 一律回落到 [DEFAULT_ICON]。
 */
object HabitIcons {

    /** 可选图标 key（展示顺序 = 这里面的顺序） */
    val availableKeys: List<String> = listOf(
        "star",
        "book",
        "school",
        "coffee",
        "water",
        "food",
        "shop",
        "wallet",
        "health",
        "movie",
        "game",
        "pet",
        "trip",
        "bus",
        "car",
        "home",
        "gift",
        "edit",
    )

    private val ICONS: Map<String, ImageVector> = mapOf(
        "star" to Icons.Rounded.Star,
        "book" to Icons.AutoMirrored.Rounded.MenuBook,
        "school" to Icons.Rounded.School,
        "coffee" to Icons.Rounded.LocalCafe,
        // 「water」是备份数据里已经在用的 key（见 BackupLegacyCompatTest），
        // 老数据不该因为这一版没收录就退化成星星图标
        "water" to Icons.Rounded.WaterDrop,
        "food" to Icons.Rounded.Restaurant,
        "shop" to Icons.Rounded.ShoppingCart,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "health" to Icons.Rounded.LocalHospital,
        "movie" to Icons.Rounded.Movie,
        "game" to Icons.Rounded.SportsEsports,
        "pet" to Icons.Rounded.Pets,
        "trip" to Icons.Rounded.Flight,
        "bus" to Icons.Rounded.DirectionsBus,
        "car" to Icons.Rounded.DirectionsCar,
        "home" to Icons.Rounded.Home,
        "gift" to Icons.Rounded.CardGiftcard,
        "edit" to Icons.Rounded.Edit,
    )

    /** 未知 key 的兜底图标（与 habits.iconKey 的历史默认值一致） */
    val DEFAULT_ICON: ImageVector = Icons.Rounded.Star

    /** 把 habit 的 iconKey 解析成图标；未知 key 返回 [DEFAULT_ICON] */
    fun resolve(iconKey: String): ImageVector = ICONS[iconKey] ?: DEFAULT_ICON
}
