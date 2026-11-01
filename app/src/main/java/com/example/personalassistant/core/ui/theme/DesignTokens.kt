package com.example.personalassistant.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =====================================================================
// 设计 token —— 樱花粉 2.0 的唯一真相源
//
// 约定：
// - 间距只用 AppSpacing 里的 6 档
// - 圆角只用 AppRadii 里的 5 档
// - 语义色（收入 / 支出 / 预算 / 优先级 / 转账 / 未知分类）
//   一律从 LocalAppColors 取，禁止在组件里写 Color(0x...)
// - 浅色 / 深色两套值由 Theme.kt 通过 CompositionLocalProvider 注入
// =====================================================================

/**
 * 间距刻度：4 / 8 / 12 / 16 / 20 / 24
 */
object AppSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
}

/**
 * 圆角刻度：chip 12 / 输入 16 / 卡片 20 / FAB 20 / 大卡 28
 *
 * 与 [AppShapes] 的对应关系见 Shape.kt —— 两者取值一致，
 * AppShapes 供 Material 3 组件使用，AppRadii 供自绘组件使用。
 */
object AppRadii {
    val chip: Dp = 12.dp
    val input: Dp = 16.dp
    val card: Dp = 20.dp
    val fab: Dp = 20.dp
    val hero: Dp = 28.dp
}

/**
 * 阴影刻度：两级暖粉阴影
 *
 * Material 3 的 CardElevation 只有不透明度、没有颜色（默认纯黑），
 * 纯黑阴影压在浅粉底上会显脏。这里给 Modifier.shadow 显式指定
 * ambient / spot 颜色为樱花粉 tinted —— 这是「温柔感」的来源。
 */
object AppShadows {
    /** 静止卡片：0 3 14 rgba(11,87,208,.10) */
    val rest = Color(0x1A0B57D0)

    /** 浮起元素（FAB / 弹出层）：0 6 22 rgba(11,87,208,.14) */
    val raised = Color(0x240B57D0)
}

/**
 * 语义色集合 —— 每组三个值：ink（文字/图标）/ container（底色）/ onContainer（底色上的文字）
 */
@Immutable
data class AppSemanticColors(
    // —— 收入 ——
    val incomeInk: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    // —— 支出 ——
    val expenseInk: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    // —— 预算 ——
    val budgetInk: Color,
    val budgetContainer: Color,
    val onBudgetContainer: Color,
    // —— 跨账户转账 ——
    val transferInk: Color,
    val transferContainer: Color,
    val onTransferContainer: Color,
    // —— 任务优先级 ——
    val priorityHighInk: Color,
    val priorityHighContainer: Color,
    val onPriorityHighContainer: Color,
    val priorityMediumInk: Color,
    val priorityMediumContainer: Color,
    val onPriorityMediumContainer: Color,
    val priorityLowInk: Color,
    val priorityLowContainer: Color,
    val onPriorityLowContainer: Color,
    // —— 中性 ——
    val unknownCategory: Color,
    val onUnknownCategory: Color,
)

/** 浅色语义色 */
val LightSemanticColors = AppSemanticColors(
    incomeInk = Color(0xFF1B5E20),
    incomeContainer = Color(0xFFE1F3E2),
    onIncomeContainer = Color(0xFF1B5E20),
    expenseInk = Color(0xFFB3261E),
    expenseContainer = Color(0xFFFDE8E6),
    onExpenseContainer = Color(0xFF8C1D18),
    budgetInk = Color(0xFF7A4A00),
    budgetContainer = Color(0xFFFFF0DC),
    onBudgetContainer = Color(0xFF7A4A00),
    transferInk = Color(0xFF37474F),
    transferContainer = Color(0xFFECEFF1),
    onTransferContainer = Color(0xFF263238),
    priorityHighInk = Color(0xFFB3261E),
    priorityHighContainer = Color(0xFFFDE8E6),
    onPriorityHighContainer = Color(0xFF8C1D18),
    priorityMediumInk = Color(0xFF7A4A00),
    priorityMediumContainer = Color(0xFFFFF0DC),
    onPriorityMediumContainer = Color(0xFF7A4A00),
    priorityLowInk = Color(0xFF1B5E20),
    priorityLowContainer = Color(0xFFE1F3E2),
    onPriorityLowContainer = Color(0xFF1B5E20),
    unknownCategory = Color(0xFF9E9E9E),
    onUnknownCategory = Color(0xFFFFFFFF),
)

/** 深色语义色 */
val DarkSemanticColors = AppSemanticColors(
    incomeInk = Color(0xFFA5D6A7),
    incomeContainer = Color(0xFF1B3A20),
    onIncomeContainer = Color(0xFFC8E6C9),
    expenseInk = Color(0xFFFFB4AB),
    expenseContainer = Color(0xFF4A1512),
    onExpenseContainer = Color(0xFFFFDAD6),
    budgetInk = Color(0xFFFFD180),
    budgetContainer = Color(0xFF3D2B00),
    onBudgetContainer = Color(0xFFFFE0B2),
    transferInk = Color(0xFFB0BEC5),
    transferContainer = Color(0xFF263238),
    onTransferContainer = Color(0xFFCFD8DC),
    priorityHighInk = Color(0xFFFFB4AB),
    priorityHighContainer = Color(0xFF4A1512),
    onPriorityHighContainer = Color(0xFFFFDAD6),
    priorityMediumInk = Color(0xFFFFD180),
    priorityMediumContainer = Color(0xFF3D2B00),
    onPriorityMediumContainer = Color(0xFFFFE0B2),
    priorityLowInk = Color(0xFFA5D6A7),
    priorityLowContainer = Color(0xFF1B3A20),
    onPriorityLowContainer = Color(0xFFC8E6C9),
    unknownCategory = Color(0xFF616161),
    onUnknownCategory = Color(0xFFFFFFFF),
)

/**
 * 语义色的读取入口。由 [PersonalAssistantTheme] 注入，深浅色各一套。
 */
val LocalAppColors = staticCompositionLocalOf { LightSemanticColors }

/**
 * 给「用户自选底色」挑一个能看清的前景色。
 *
 * 分类色、课程色都是用户自己挑的，可能很浅（浅黄 / 浅青）也可能是深色。
 * 一律用白图标会看不见，一律用黑图标在深色上又看不见 ——
 * 这里按相对亮度（WCAG 定义）自动选：亮底配深字，暗底配白字。
 */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF20191B) else Color(0xFFFFFFFF)
