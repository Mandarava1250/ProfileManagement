package com.example.personalassistant.feature.finance.presentation.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
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
        "Restaurant" to Icons.Filled.Restaurant,
        "DirectionsBus" to Icons.Filled.DirectionsBus,
        "ShoppingCart" to Icons.Filled.ShoppingCart,
        "SportsEsports" to Icons.Filled.SportsEsports,
        "Home" to Icons.Filled.Home,
        "LocalHospital" to Icons.Filled.LocalHospital,
        "MenuBook" to Icons.Filled.MenuBook,
        "Category" to Icons.Filled.Category,
        "AccountBalanceWallet" to Icons.Filled.AccountBalanceWallet,
        // 7b 新增
        "LocalCafe" to Icons.Filled.LocalCafe,
        "School" to Icons.Filled.School,
        "Movie" to Icons.Filled.Movie,
        "Pets" to Icons.Filled.Pets,
        "Flight" to Icons.Filled.Flight,
        "CardGiftcard" to Icons.Filled.CardGiftcard,
        "DirectionsCar" to Icons.Filled.DirectionsCar,
    )

    val DEFAULT_ICON: ImageVector = Icons.Filled.Category

    /** 把 CategoryEntity.iconName 转成 ImageVector；未知时返回 [DEFAULT_ICON] */
    fun resolve(iconName: String): ImageVector =
        ICONS[iconName] ?: DEFAULT_ICON
}