package com.example.personalassistant.feature.finance.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import org.junit.Rule
import org.junit.Test

/**
 * Phase 9b D2：`BudgetProgressBar` 的 Compose UI 测试（androidTest / instrumented）。
 *
 * ## 覆盖
 * - 正常渲染：标题 + 百分比 + 已用/总额摘要
 * - 未设预算（progress = null）：整块不渲染（不显示空壳）
 * - 超支：百分比 > 100%，点击展开后显示「已超出预算」
 * - 点击展开：先只显示摘要，展开后出现「剩余」明细
 *
 * ## 写法要点
 *
 * 金额文案用 `MoneyFormat.formatPlain` 现算 —— 它带千分位（`1,000.00`），
 * 硬编码会失配（这是 D1 本轮真实踩过的坑）。
 *
 * ## ⚠️ 方法名必须是英文
 *
 * androidTest 的类要被 D8 打包成 dex，方法名不能含空格。
 */
class BudgetProgressBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun money(v: Double) = MoneyFormat.formatPlain(v)

    private fun setContent(progress: BudgetProgress?) {
        composeTestRule.setContent {
            Column {
                BudgetProgressBar(progress = progress)
            }
        }
    }

    // ===== 正常渲染 =====

    /** 50% 场景：标题 + 百分比 + 摘要都渲染 */
    @Test
    fun renders_title_percent_and_summary() {
        setContent(BudgetProgress(budgetAmount = 1000.0, spentAmount = 500.0))

        composeTestRule.onNodeWithText("本月预算").assertIsDisplayed()
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("已用 ${money(500.0)} / ${money(1000.0)}")
            .assertIsDisplayed()
    }

    /** 未设预算（null）→ 整块不渲染 */
    @Test
    fun null_progress_renders_nothing() {
        setContent(null)

        composeTestRule.onNodeWithText("本月预算").assertDoesNotExist()
    }

    /** 一分没花 → 0% */
    @Test
    fun zero_spent_shows_zero_percent() {
        setContent(BudgetProgress(budgetAmount = 1000.0, spentAmount = 0.0))

        composeTestRule.onNodeWithText("0%").assertIsDisplayed()
    }

    // ===== 展开前 / 后 =====

    /** 默认收起：不显示「剩余」明细 */
    @Test
    fun collapsed_hides_detail_rows() {
        setContent(BudgetProgress(budgetAmount = 1000.0, spentAmount = 300.0))

        composeTestRule.onNodeWithText("剩余 ${money(700.0)}").assertDoesNotExist()
    }

    /** 点击展开：显示「剩余」 */
    @Test
    fun click_expands_to_show_remaining() {
        setContent(BudgetProgress(budgetAmount = 1000.0, spentAmount = 300.0))

        composeTestRule.onNodeWithText("本月预算").performClick()

        composeTestRule.onNodeWithText("剩余 ${money(700.0)}").assertIsDisplayed()
    }

    /** 再点一次收起 */
    @Test
    fun click_twice_collapses_again() {
        setContent(BudgetProgress(budgetAmount = 1000.0, spentAmount = 300.0))

        composeTestRule.onNodeWithText("本月预算").performClick()
        composeTestRule.onNodeWithText("剩余 ${money(700.0)}").assertIsDisplayed()

        composeTestRule.onNodeWithText("本月预算").performClick()
        composeTestRule.onNodeWithText("剩余 ${money(700.0)}").assertDoesNotExist()
    }

    // ===== 超支 =====

    /** 超支：百分比 > 100% */
    @Test
    fun over_budget_shows_over_hundred_percent() {
        setContent(BudgetProgress(budgetAmount = 1000.0, spentAmount = 1500.0))

        composeTestRule.onNodeWithText("150%").assertIsDisplayed()
    }

    /** 超支 + 展开：显示「已超出预算」提示 */
    @Test
    fun over_budget_expanded_shows_exceeded_warning() {
        setContent(BudgetProgress(budgetAmount = 1000.0, spentAmount = 1500.0))

        composeTestRule.onNodeWithText("本月预算").performClick()

        composeTestRule
            .onNodeWithText("已超出预算 ${money(500.0)}")
            .assertIsDisplayed()
    }

    /** 未超支 + 展开：不显示「已超出预算」 */
    @Test
    fun under_budget_expanded_has_no_warning() {
        setContent(BudgetProgress(budgetAmount = 1000.0, spentAmount = 300.0))

        composeTestRule.onNodeWithText("本月预算").performClick()

        composeTestRule
            .onNodeWithText("已超出预算 ${money(700.0)}")
            .assertDoesNotExist()
    }
}