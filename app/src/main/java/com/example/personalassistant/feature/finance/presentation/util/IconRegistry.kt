package com.example.personalassistant.feature.finance.presentation.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
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
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Phase 7b：分类 icon 名称 → ImageVector 映射
 *
 * - 默认内置分类的 iconName 字符串（如 "Restaurant"）→ Material Icons ImageVector
 * - 用户自定义分类若 iconName 未知 → fallback 到 [DEFAULT_ICON]
 * - 共 16 个 icon 供 IconPickerSheet 选择
 *
 * ## 扩展点
 * - 加新 icon：在 [ICONS] 加一行
 * - 后续支持自定义 icon：把 iconName 改成存储字符串 + 本地 Asset / Drawable 解析
 */
object IconRegistry {

    /** 所有可选 icon 名称（按排序）—— IconPickerSheet 渲染用 */
    val availableIconNames: List<String> = listOf(
        "AccountBalanceWallet",
        "CardGiftcard",
        "Category",
        "DirectionsBus",
        "DirectionsCar",
        "Flight",
        "Home",
        "LocalCafe",
        "LocalHospital",
        "MenuBook",
        "Movie",
        "Pets",
        "Restaurant",
        "School",
        "ShoppingCart",
        "SportsEsports",
    )

    private val ICONS: Map<String, ImageVector> = mapOf(
        "Restaurant" to Icons.Rounded.Restaurant,
        "DirectionsBus" to Icons.Rounded.DirectionsBus,
        "ShoppingCart" to Icons.Rounded.ShoppingCart,
        "SportsEsports" to Icons.Rounded.SportsEsports,
        "Home" to Icons.Rounded.Home,
        "LocalHospital" to Icons.Rounded.LocalHospital,
        "MenuBook" to Icons.Rounded.MenuBook,
        "Category" to Icons.Rounded.Category,
        "AccountBalanceWallet" to Icons.Rounded.AccountBalanceWallet,
        // 7b 新增
        "LocalCafe" to Icons.Rounded.LocalCafe,
        "School" to Icons.Rounded.School,
        "Movie" to Icons.Rounded.Movie,
        "Pets" to Icons.Rounded.Pets,
        "Flight" to Icons.Rounded.Flight,
        "CardGiftcard" to Icons.Rounded.CardGiftcard,
        "DirectionsCar" to Icons.Rounded.DirectionsCar,
        // Phase 5：内置「转账」分类的图标。
        // 故意**不**加进 availableIconNames：那是 IconPickerSheet 的渲染源，
        // 加了就等于让用户能给自己建的收支分类选上「转账」图标，
        // 一笔消费被标成转账语义 —— 不该给这个选项。
        "SwapHoriz" to Icons.Rounded.SwapHoriz,
    )

    val DEFAULT_ICON: ImageVector = Icons.Rounded.Category

    /** 把 CategoryEntity.iconName 转成 ImageVector；未知时返回 [DEFAULT_ICON] */
    fun resolve(iconName: String): ImageVector =
        ICONS[iconName] ?: DEFAULT_ICON
}