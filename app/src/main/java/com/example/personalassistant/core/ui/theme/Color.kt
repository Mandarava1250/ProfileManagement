package com.example.personalassistant.core.ui.theme

import androidx.compose.ui.graphics.Color

// =====================================================================
// Material 3 主色板 — 选用「樱花粉」作为主色调，呼应产品温柔陪伴感
// =====================================================================

// Light scheme
val PrimaryLight = Color(0xFFD81B60)            // 樱花粉
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFFFE3EC)   // Phase 14：调暖一档，粉调更柔
val OnPrimaryContainerLight = Color(0xFF3E001B)

val SecondaryLight = Color(0xFF74565F)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFFFE3EC)
val OnSecondaryContainerLight = Color(0xFF2B151C)

val TertiaryLight = Color(0xFF7C5635)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFDCC2)
val OnTertiaryContainerLight = Color(0xFF2E1500)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFFFFBFC)         // Phase 14：比纯白更暖一点
val OnBackgroundLight = Color(0xFF20191B)
val SurfaceLight = Color(0xFFFFFBFC)
val OnSurfaceLight = Color(0xFF20191B)
val SurfaceVariantLight = Color(0xFFF2DDE1)
val OnSurfaceVariantLight = Color(0xFF514347)
val OutlineLight = Color(0xFF837377)
val OutlineVariantLight = Color(0xFFD5C2C6)
val ScrimLight = Color(0xFF000000)

// Dark scheme
val PrimaryDark = Color(0xFFFFB1C8)
val OnPrimaryDark = Color(0xFF65002F)
val PrimaryContainerDark = Color(0xFF8E0044)
val OnPrimaryContainerDark = Color(0xFFFFD9E2)

val SecondaryDark = Color(0xFFE3BDC6)
val OnSecondaryDark = Color(0xFF422931)
val SecondaryContainerDark = Color(0xFF5A3F47)
val OnSecondaryContainerDark = Color(0xFFFFD9E2)

val TertiaryDark = Color(0xFFEEBE91)
val OnTertiaryDark = Color(0xFF482909)
val TertiaryContainerDark = Color(0xFF623F1F)
val OnTertiaryContainerDark = Color(0xFFFFDCC2)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF1A1112)
val OnBackgroundDark = Color(0xFFEDE0E2)
val SurfaceDark = Color(0xFF1A1112)
val OnSurfaceDark = Color(0xFFEDE0E2)
val SurfaceVariantDark = Color(0xFF514347)
val OnSurfaceVariantDark = Color(0xFFD5C2C6)
val OutlineDark = Color(0xFF9E8C90)
val OutlineVariantDark = Color(0xFF514347)
val ScrimDark = Color(0xFF000000)

// =====================================================================
// 零钱池与记账专用色（业务色板，与主题色区分）
// =====================================================================
val IncomeGreen = Color(0xFF2E7D32)
val ExpenseRed = Color(0xFFC62828)
val BudgetYellow = Color(0xFFF9A825)
val BudgetAlertRed = Color(0xFFD32F2F)

// 任务优先级色
val PriorityHigh = Color(0xFFD32F2F)
val PriorityMedium = Color(0xFFF9A825)
val PriorityLow = Color(0xFF388E3C)

// 课程默认 8 色（课程表色卡）
val CoursePalette: List<Color> = listOf(
    Color(0xFFE57373), // 浅红
    Color(0xFFFFB74D), // 浅橙
    Color(0xFFFFD54F), // 浅黄
    Color(0xFF81C784), // 浅绿
    Color(0xFF4DD0E1), // 浅青
    Color(0xFF64B5F6), // 浅蓝
    Color(0xFF9575CD), // 浅紫
    Color(0xFFF06292)  // 浅粉
)
