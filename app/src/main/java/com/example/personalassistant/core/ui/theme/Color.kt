package com.example.personalassistant.core.ui.theme

import androidx.compose.ui.graphics.Color

// =====================================================================
// Material 3 主色板 — 选用「静谧蓝」作为主色调
//
// Phase 14 原为樱花粉，伙伴定调改成蓝色。
// 蓝色比粉色更适合「工具型 App」：长时间看不刺眼，
// 课程色 / 事件色的多彩标签在蓝底上也不会打架。
// =====================================================================

// Light scheme
val PrimaryLight = Color(0xFF0B57D0)            // 静谧蓝
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFD8E2FF)
val OnPrimaryContainerLight = Color(0xFF001A41)

val SecondaryLight = Color(0xFF565E71)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFDAE2F9)
val OnSecondaryContainerLight = Color(0xFF131C2B)

val TertiaryLight = Color(0xFF6B5778)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFF3DAFF)
val OnTertiaryContainerLight = Color(0xFF251431)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFFAFBFF)         // 冷调白
val OnBackgroundLight = Color(0xFF1A1B20)
val SurfaceLight = Color(0xFFFAFBFF)
val OnSurfaceLight = Color(0xFF1A1B20)
val SurfaceVariantLight = Color(0xFFE0E2EC)
val OnSurfaceVariantLight = Color(0xFF44474E)
val OutlineLight = Color(0xFF74777F)
val OutlineVariantLight = Color(0xFFC4C6D0)
val ScrimLight = Color(0xFF000000)

// Dark scheme
val PrimaryDark = Color(0xFFADC6FF)
val OnPrimaryDark = Color(0xFF002E69)
val PrimaryContainerDark = Color(0xFF004494)
val OnPrimaryContainerDark = Color(0xFFD8E2FF)

val SecondaryDark = Color(0xFFBEC6DC)
val OnSecondaryDark = Color(0xFF283041)
val SecondaryContainerDark = Color(0xFF3E4759)
val OnSecondaryContainerDark = Color(0xFFDAE2F9)

val TertiaryDark = Color(0xFFD7BEE4)
val OnTertiaryDark = Color(0xFF3B2948)
val TertiaryContainerDark = Color(0xFF523F5F)
val OnTertiaryContainerDark = Color(0xFFF3DAFF)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF1A1B20)
val OnBackgroundDark = Color(0xFFE3E2E6)
val SurfaceDark = Color(0xFF1A1B20)
val OnSurfaceDark = Color(0xFFE3E2E6)
val SurfaceVariantDark = Color(0xFF44474E)
val OnSurfaceVariantDark = Color(0xFFC4C6D0)
val OutlineDark = Color(0xFF8E9099)
val OutlineVariantDark = Color(0xFF44474E)
val ScrimDark = Color(0xFF000000)

// =====================================================================
// 零钱池与记账专用色（业务色板，与主题色区分）
//
// 这些是「收入绿 / 支出红 / 预算黄」，属于业务语义 —— 不随主题主色变。
// 用户换了主色也不该让「超支」变成蓝色。
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
