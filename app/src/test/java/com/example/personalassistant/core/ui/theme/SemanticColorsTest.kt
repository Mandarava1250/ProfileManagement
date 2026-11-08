package com.example.personalassistant.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 14 阶段七：语义色 token 单测（纯 JVM）
 *
 * 被测对象：`core/ui/theme/DesignTokens.kt` 里的
 * [LightSemanticColors] / [DarkSemanticColors]（每组三值：ink / container / onContainer）。
 *
 * 覆盖：
 * - 浅色 / 深色两套取值不同（收入 / 支出 / 预算的 ink 色不相等）
 * - 每组 container 与 ink、onContainer 都不相同（底色不能和上面的文字糊在一起）
 * - 深色主题下 ink 与 onContainer 也不相同
 * - 深浅两套的 container / onContainer 逐组不相等
 * - 中性色（未知分类）：unknownCategory 深浅不同，onUnknownCategory 两套都是纯白
 *
 * 口径说明（为什么浅色主题不断言 ink != onContainer）：
 * 浅色主题里「收入 / 预算 / 中低优先级」这几组刻意让 ink == onContainer
 * ——同一个深色既画在普通底上，也画在浅色 container 上，这是设计上的复用。
 * 真正必须区分的是 container 与前景色，所以那条对深浅两套都断言；
 * 「ink != onContainer」只在深色主题断言（那里两组值本来就不同）。
 */
class SemanticColorsTest {

    /** 一组语义色三元组：ink（文字/图标）/ container（底色）/ onContainer（底色上的文字） */
    private data class Group(
        val name: String,
        val ink: Color,
        val container: Color,
        val onContainer: Color,
    )

    private val lightGroups = listOf(
        Group(
            "收入",
            LightSemanticColors.incomeInk,
            LightSemanticColors.incomeContainer,
            LightSemanticColors.onIncomeContainer,
        ),
        Group(
            "支出",
            LightSemanticColors.expenseInk,
            LightSemanticColors.expenseContainer,
            LightSemanticColors.onExpenseContainer,
        ),
        Group(
            "预算",
            LightSemanticColors.budgetInk,
            LightSemanticColors.budgetContainer,
            LightSemanticColors.onBudgetContainer,
        ),
        Group(
            "转账",
            LightSemanticColors.transferInk,
            LightSemanticColors.transferContainer,
            LightSemanticColors.onTransferContainer,
        ),
        Group(
            "当前时刻",
            LightSemanticColors.nowInk,
            LightSemanticColors.nowContainer,
            LightSemanticColors.onNowContainer,
        ),
        Group(
            "高优先级",
            LightSemanticColors.priorityHighInk,
            LightSemanticColors.priorityHighContainer,
            LightSemanticColors.onPriorityHighContainer,
        ),
        Group(
            "中优先级",
            LightSemanticColors.priorityMediumInk,
            LightSemanticColors.priorityMediumContainer,
            LightSemanticColors.onPriorityMediumContainer,
        ),
        Group(
            "低优先级",
            LightSemanticColors.priorityLowInk,
            LightSemanticColors.priorityLowContainer,
            LightSemanticColors.onPriorityLowContainer,
        ),
    )

    private val darkGroups = listOf(
        Group(
            "收入",
            DarkSemanticColors.incomeInk,
            DarkSemanticColors.incomeContainer,
            DarkSemanticColors.onIncomeContainer,
        ),
        Group(
            "支出",
            DarkSemanticColors.expenseInk,
            DarkSemanticColors.expenseContainer,
            DarkSemanticColors.onExpenseContainer,
        ),
        Group(
            "预算",
            DarkSemanticColors.budgetInk,
            DarkSemanticColors.budgetContainer,
            DarkSemanticColors.onBudgetContainer,
        ),
        Group(
            "转账",
            DarkSemanticColors.transferInk,
            DarkSemanticColors.transferContainer,
            DarkSemanticColors.onTransferContainer,
        ),
        Group(
            "当前时刻",
            DarkSemanticColors.nowInk,
            DarkSemanticColors.nowContainer,
            DarkSemanticColors.onNowContainer,
        ),
        Group(
            "高优先级",
            DarkSemanticColors.priorityHighInk,
            DarkSemanticColors.priorityHighContainer,
            DarkSemanticColors.onPriorityHighContainer,
        ),
        Group(
            "中优先级",
            DarkSemanticColors.priorityMediumInk,
            DarkSemanticColors.priorityMediumContainer,
            DarkSemanticColors.onPriorityMediumContainer,
        ),
        Group(
            "低优先级",
            DarkSemanticColors.priorityLowInk,
            DarkSemanticColors.priorityLowContainer,
            DarkSemanticColors.onPriorityLowContainer,
        ),
    )

    // ===== 浅色 / 深色两套取值不同 =====

    @Test
    fun `income expense and budget ink differ between light and dark`() {
        assertDifferent(
            LightSemanticColors.incomeInk,
            DarkSemanticColors.incomeInk,
            "收入 ink：深浅两套必须不同",
        )
        assertDifferent(
            LightSemanticColors.expenseInk,
            DarkSemanticColors.expenseInk,
            "支出 ink：深浅两套必须不同",
        )
        assertDifferent(
            LightSemanticColors.budgetInk,
            DarkSemanticColors.budgetInk,
            "预算 ink：深浅两套必须不同",
        )
    }

    @Test
    fun `container differs between light and dark for every group`() {
        lightGroups.forEachIndexed { index, light ->
            val dark = darkGroups[index]
            assertDifferent(
                light.container,
                dark.container,
                "${light.name} container：深浅两套必须不同",
            )
        }
    }

    @Test
    fun `onContainer differs between light and dark for every group`() {
        lightGroups.forEachIndexed { index, light ->
            val dark = darkGroups[index]
            assertDifferent(
                light.onContainer,
                dark.onContainer,
                "${light.name} onContainer：深浅两套必须不同",
            )
        }
    }

    // ===== 每组三个值不能糊在一起 =====

    @Test
    fun `container differs from ink and onContainer in light theme`() {
        lightGroups.forEach { group ->
            assertDifferent(
                group.container,
                group.ink,
                "浅色·${group.name}：container 不能和 ink 同色",
            )
            assertDifferent(
                group.container,
                group.onContainer,
                "浅色·${group.name}：container 不能和 onContainer 同色",
            )
        }
    }

    @Test
    fun `container differs from ink and onContainer in dark theme`() {
        darkGroups.forEach { group ->
            assertDifferent(
                group.container,
                group.ink,
                "深色·${group.name}：container 不能和 ink 同色",
            )
            assertDifferent(
                group.container,
                group.onContainer,
                "深色·${group.name}：container 不能和 onContainer 同色",
            )
        }
    }

    @Test
    fun `ink differs from onContainer in dark theme`() {
        darkGroups.forEach { group ->
            assertDifferent(
                group.ink,
                group.onContainer,
                "深色·${group.name}：ink 不能和 onContainer 同色",
            )
        }
    }

    // ===== 中性色（未知分类）=====

    @Test
    fun `unknown category color differs between themes and its foreground stays white`() {
        assertDifferent(
            LightSemanticColors.unknownCategory,
            DarkSemanticColors.unknownCategory,
            "未知分类底色：深浅两套必须不同",
        )
        assertSame(
            LightSemanticColors.onUnknownCategory,
            Color(0xFFFFFFFF),
            "浅色·未知分类：前景色必须是白色",
        )
        assertSame(
            DarkSemanticColors.onUnknownCategory,
            Color(0xFFFFFFFF),
            "深色·未知分类：前景色必须是白色",
        )
    }

    // ===== 辅助 =====

    private fun assertDifferent(a: Color, b: Color, message: String) {
        assertFalse(message, a == b)
    }

    private fun assertSame(a: Color, b: Color, message: String) {
        assertTrue(message, a == b)
    }
}
