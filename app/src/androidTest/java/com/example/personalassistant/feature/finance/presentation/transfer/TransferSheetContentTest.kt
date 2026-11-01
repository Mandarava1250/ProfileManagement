package com.example.personalassistant.feature.finance.presentation.transfer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.personalassistant.feature.finance.domain.model.AccountBalance
import com.example.personalassistant.feature.finance.domain.model.TransferAccountOption
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Phase 9b D1：`TransferSheetContent` 的 Compose UI 测试（androidTest / instrumented）。
 *
 * ## 覆盖 plan §4.3 要求的三个场景
 *
 * - **happy path**：金额合法 + 选中账户 → 按钮可用 → 点击抛出正确 accountId
 * - **源==目标拦截**：当前账户**不出现在转入列表**（状态层就过滤掉，比「点了才报错」更早一步）
 * - **余额不足**：超余额 → 错误提示文案 + 按钮禁用
 *
 * ## 两个必须记住的写法要点（都是本轮真实踩到后改对的）
 *
 * 1. **金额文案不能硬编码**。`MoneyFormat.formatPlain` 用 `NumberFormat(Locale.CHINA)`，
 *    1000.0 渲染成 `1,000.00` **带千分位**。硬写 `"1000.00"` 必然失配。
 *    所以期望值一律用 `MoneyFormat.formatPlain(x)` 现算，让断言跟着实现走。
 *
 * 2. **内容在 `verticalScroll` 里，屏外节点 `assertIsDisplayed` 会失败**。
 *    Content 高度超过一屏时（标题 + 转出说明 + 账户行 + 2 个输入框 + 摘要 + 按钮），
 *    靠下的节点需要先 `performScrollTo()`。
 *    另外 **`assertIsNotEnabled` 只需节点存在**，不需要可见，所以禁用类断言可直接写。
 *
 * ## 为什么直接喂 state（不起 Hilt）
 *
 * [TransferSheetContent] 是**无状态**的（state in / callback out）。
 * 起 Hilt 会引入 `FinanceViewModel` 的 10+ 依赖，把「DataStore 能不能读」
 * 这类与渲染无关的变量混进来 —— 那部分由 `TransferFormStateTest`（JVM，16 例）覆盖。
 *
 * ## ⚠️ 方法名必须是英文
 *
 * androidTest 的类要被 D8 打包成 dex，旧 DEX 格式不允许方法名含空格
 * （项目踩坑记录：`D8: Space characters in SimpleName ... not allowed`）。
 */
class TransferSheetContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val target1 = TransferAccountOption(2L, "旅行账本", 300.0)
    private val target2 = TransferAccountOption(3L, "储蓄卡", 5000.0)

    /** 现算金额文案，避免与 NumberFormat 的千分位规则脱节 */
    private fun money(v: Double) = MoneyFormat.formatPlain(v)

    private fun setContent(
        state: TransferFormState,
        onAmountChange: (String) -> Unit = {},
        onNoteChange: (String) -> Unit = {},
        onTargetChange: (Long) -> Unit = {},
        onSubmit: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            Column {
                TransferSheetContent(
                    state = state,
                    onAmountChange = onAmountChange,
                    onNoteChange = onNoteChange,
                    onTargetChange = onTargetChange,
                    onSubmit = onSubmit,
                )
            }
        }
    }

    private fun baseState(
        amountText: String = "",
        targetId: Long? = null,
        targets: List<TransferAccountOption> = listOf(target1, target2),
    ) = TransferFormState(
        targets = targets,
        fromName = "默认账户",
        fromBalance = 1000.0,
        amountText = amountText,
        targetId = targetId,
    )

    // ===== 基础渲染 =====

    /** 标题 + 转出方说明 + 两个账户按钮都渲染 */
    @Test
    fun renders_header_and_targets() {
        setContent(baseState())

        composeTestRule.onNodeWithText("账户间转账").assertExists()
        composeTestRule
            .onNodeWithText("从「默认账户」转出，当前余额 ${money(1000.0)}")
            .assertExists()
        composeTestRule.onNodeWithText("转入账户").assertExists()
        composeTestRule.onNodeWithText("旅行账本").assertExists()
        composeTestRule.onNodeWithText("储蓄卡").assertExists()
    }

    /** 首个账户按钮可见（首屏内） */
    @Test
    fun first_target_is_visible_without_scrolling() {
        setContent(baseState())

        composeTestRule.onNodeWithText("账户间转账").assertIsDisplayed()
        composeTestRule.onNodeWithText("旅行账本").assertIsDisplayed()
    }

    /** 只剩一个账户：显示原因说明，不显示账户列表，按钮禁用 */
    @Test
    fun single_account_shows_explanation_and_disables_submit() {
        setContent(baseState(targets = emptyList()))

        composeTestRule
            .onNodeWithText("至少需要两个账户才能转账，请到「设置 - 账户」新建一个。")
            .assertExists()
        composeTestRule.onNodeWithText("转入账户").assertDoesNotExist()
        composeTestRule.onNodeWithText("确认转账").assertIsNotEnabled()
    }

    // ===== 场景 1：happy path =====

    /** 金额合法 + 选中账户 → 按钮可用；滚到按钮并点击 → 抛出正确 accountId */
    @Test
    fun happy_path_enables_submit_and_emits_target_id() {
        var submitted: Long? = null
        setContent(
            state = baseState(amountText = "100", targetId = 2L),
            onSubmit = { submitted = it },
        )

        composeTestRule.onNodeWithText("确认转账").assertIsEnabled()
        composeTestRule.onNodeWithText("确认转账").performScrollTo().performClick()

        assertEquals(2L, submitted)
    }

    /** 摘要行显示完整资金流向 */
    @Test
    fun happy_path_shows_confirm_summary() {
        setContent(baseState(amountText = "100", targetId = 2L))

        composeTestRule
            .onNodeWithText("确认后：默认账户 −100 → 旅行账本")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** 只有金额、没选账户 → 按钮禁用，且无摘要 */
    @Test
    fun amount_without_target_disables_submit() {
        setContent(baseState(amountText = "100", targetId = null))

        composeTestRule.onNodeWithText("确认转账").assertIsNotEnabled()
        composeTestRule.onNodeWithText("确认后：默认账户 −100 → 旅行账本").assertDoesNotExist()
    }

    /** 只有账户、没填金额 → 按钮禁用 */
    @Test
    fun target_without_amount_disables_submit() {
        setContent(baseState(amountText = "", targetId = 2L))

        composeTestRule.onNodeWithText("确认转账").assertIsNotEnabled()
    }

    // ===== 场景 2：源 == 目标（状态层已排除） =====

    /**
     * 当前账户**根本不出现在转入列表**里。
     *
     * 这是 plan 「源==目标 拦截」在 UI 层的表现：不是让人点了才报错，
     * 而是这个选项压根不存在（推导见 [TransferFormState.from] 的 filter）。
     */
    @Test
    fun current_account_is_not_offered_as_target() {
        val state = TransferFormState.from(
            balances = listOf(
                AccountBalance(
                    accountId = 1L,
                    accountName = "默认账户",
                    initialBalance = 1000.0,
                    income = 0.0,
                    expense = 0.0,
                    transferNet = 0.0,
                ),
                AccountBalance(
                    accountId = 2L,
                    accountName = "旅行账本",
                    initialBalance = 300.0,
                    income = 0.0,
                    expense = 0.0,
                    transferNet = 0.0,
                ),
            ),
            currentAccountId = 1L,
        )
        setContent(state)

        // 「默认账户」只作为转出方出现在说明文案里
        composeTestRule
            .onNodeWithText("从「默认账户」转出，当前余额 ${money(1000.0)}")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("旅行账本").assertIsDisplayed()
        // 关键断言：当前账户不作为可点选项出现
        composeTestRule.onNodeWithText("默认账户").assertDoesNotExist()
    }

    // ===== 场景 3：余额不足 =====

    /** 超出余额 → 错误提示 + 按钮禁用 */
    @Test
    fun over_balance_shows_error_and_disables_submit() {
        setContent(baseState(amountText = "5000", targetId = 2L))

        composeTestRule
            .onNodeWithText("超出余额：当前 ${money(1000.0)}")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("确认转账").assertIsNotEnabled()
    }

    /** 恰好等于余额 → 不算超（边界：可以全部转出） */
    @Test
    fun amount_equal_to_balance_is_allowed() {
        setContent(baseState(amountText = "1000", targetId = 2L))

        composeTestRule.onNodeWithText("超出余额：当前 ${money(1000.0)}").assertDoesNotExist()
        composeTestRule.onNodeWithText("确认转账").assertIsEnabled()
    }

    /** 转出方余额未知 → 不误报超余额，走兜底文案 */
    @Test
    fun unknown_balance_never_shows_error() {
        setContent(
            TransferFormState(
                targets = listOf(target1),
                fromName = null,
                fromBalance = null,
                amountText = "99999",
                targetId = 2L,
            ),
        )

        composeTestRule.onNodeWithText("从当前账户转出").assertIsDisplayed()
        composeTestRule.onNodeWithText("确认转账").assertIsEnabled()
    }

    // ===== 交互回调 =====

    /** 点账户按钮 → 回调带出该 accountId */
    @Test
    fun clicking_target_emits_account_id() {
        var picked: Long? = null
        setContent(baseState(), onTargetChange = { picked = it })

        composeTestRule.onNodeWithText("储蓄卡").performScrollTo().performClick()

        assertEquals(3L, picked)
    }

    /** 金额输入 → 回调带出输入文本 */
    @Test
    fun typing_amount_emits_text() {
        var amount: String? = null
        setContent(baseState(), onAmountChange = { amount = it })

        composeTestRule.onNodeWithText("金额").performScrollTo().performTextReplacement("250")

        assertEquals("250", amount)
    }

    /** 备注输入 → 回调带出输入文本 */
    @Test
    fun typing_note_emits_text() {
        var note: String? = null
        setContent(baseState(), onNoteChange = { note = it })

        composeTestRule
            .onNodeWithText("备注（可选）")
            .performScrollTo()
            .performTextReplacement("还信用卡")

        assertEquals("还信用卡", note)
    }
}