package com.example.personalassistant.feature.profile.presentation.accountswitcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Phase 9b B2：AccountSwitcherBar 的 Compose UI 测试（androidTest / instrumented）。
 *
 * ## 为什么直接喂 state，不起 Hilt
 * 本组件是**无状态**的（state in / callback out），用它自己的入参就能覆盖全部渲染分支；
 * 起 Hilt 只会把「DataStore 能不能读」这类与渲染无关的变量混进来 ——
 * 那部分由 [AccountSwitcherViewModelTest] 在 JVM 覆盖，快得多。
 *
 * ## 覆盖的渲染分支
 * - 多账户 → 每个账户一个 Chip，都能点到
 * - 单账户 → 整条不渲染（`isVisible=false` 的边界）
 * - 加载中 → 整条不渲染（避免闪一帧空条）
 * - 点击 → 回调带出正确的 accountId
 *
 * ## ⚠️ 方法名为什么必须是英文（踩坑记录）
 * 本项目 JVM 单测普遍使用「中文 + 空格」的反引号方法名（如 `` `editing default is empty` ``），
 * 在 JVM 上完全正常 —— 但 androidTest 的类要被 D8 打包成 dex，
 * **旧 DEX 格式不允许方法名含空格**，会直接炸在 `:app:dexBuilderDebugAndroidTest`：
 * ```
 * D8: Space characters in SimpleName '多账户时渲染每个账户的 Chip' are not allowed prior to DEX version 040
 * ```
 * 该错误发生在 dex 打包阶段，Kotlin 编译期看不出来，排查时容易误判成依赖/环境问题。
 * 因此 androidTest 一律用英文下划线方法名，中文说明放注释里。
 */
class AccountSwitcherBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val accounts = listOf(
        AccountSwitcherItem(id = 1L, name = "默认账户"),
        AccountSwitcherItem(id = 2L, name = "旅行账本"),
        AccountSwitcherItem(id = 3L, name = "副卡"),
    )

    private fun setContent(
        state: AccountSwitcherState,
        onSwitch: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            Column {
                AccountSwitcherBar(state = state, onSwitch = onSwitch)
            }
        }
    }

    // ===== 渲染分支 =====

    /** 多账户：每个账户都渲染出可点的 Chip */
    @Test
    fun renders_chip_for_each_account_when_multiple_accounts() {
        setContent(
            AccountSwitcherState(
                isLoading = false,
                accounts = accounts,
                currentAccountId = 1L,
            ),
        )

        composeTestRule.onNodeWithText("默认账户").assertIsDisplayed()
        composeTestRule.onNodeWithText("旅行账本").assertIsDisplayed()
        composeTestRule.onNodeWithText("副卡").assertIsDisplayed()
    }

    /** 单账户：整条不渲染（切换没有信息量） */
    @Test
    fun hides_bar_when_single_account() {
        setContent(
            AccountSwitcherState(
                isLoading = false,
                accounts = listOf(accounts.first()),
                currentAccountId = 1L,
            ),
        )

        composeTestRule.onNodeWithText("默认账户").assertDoesNotExist()
    }

    /** 加载中：整条不渲染（避免闪一帧空条） */
    @Test
    fun hides_bar_while_loading() {
        setContent(AccountSwitcherState(isLoading = true, accounts = accounts))

        composeTestRule.onNodeWithText("默认账户").assertDoesNotExist()
    }

    // ===== 交互 =====

    /** 点击非当前账户：回调该账户 id */
    @Test
    fun click_non_current_chip_emits_that_account_id() {
        var switchedTo: Long? = null
        setContent(
            state = AccountSwitcherState(
                isLoading = false,
                accounts = accounts,
                currentAccountId = 1L,
            ),
            onSwitch = { switchedTo = it },
        )

        composeTestRule.onNodeWithText("旅行账本").performClick()

        assertEquals("应回调被点击账户的 id", 2L, switchedTo)
    }

    /**
     * 点击当前账户：组件同样回调。
     *
     * 组件的职责是「如实上报点击」，而「点自己就吞掉」是业务规则 —— 那条规则只住在
     * [AccountSwitcherViewModel.switchTo]，放两份就会出现「组件改了、VM 没改」的不一致。
     */
    @Test
    fun click_current_chip_still_emits_its_id() {
        var switchedTo: Long? = null
        setContent(
            state = AccountSwitcherState(
                isLoading = false,
                accounts = accounts,
                currentAccountId = 1L,
            ),
            onSwitch = { switchedTo = it },
        )

        composeTestRule.onNodeWithText("默认账户").performClick()

        assertEquals(1L, switchedTo)
    }

    /** 切换中：Chip 禁用，点击不回调（避免连点产生多次 DataStore 写） */
    @Test
    fun click_is_ignored_while_switching() {
        var switchedTo: Long? = null
        setContent(
            state = AccountSwitcherState(
                isLoading = false,
                accounts = accounts,
                currentAccountId = 1L,
                isSwitching = true,
            ),
            onSwitch = { switchedTo = it },
        )

        composeTestRule.onNodeWithText("旅行账本").performClick()

        assertEquals("写 DataStore 期间不该再收一次点击", null, switchedTo)
    }

    // ===== 语义（无障碍） =====

    /**
     * 选中态通过 Material3 自带的 selectable 语义表达。
     *
     * 曾经手写过 `contentDescription = "账户名，当前账户"`，两个问题：
     * 1. 会覆盖文本语义，`onNodeWithText("旅行账本")` 直接失配（本用例就是那样失败的）；
     * 2. 冗余 —— FilterChip 内部已是 selectable，TalkBack 本来就会念选中态。
     * 所以这里断言原生语义，而不是断言自己加的文案。
     */
    @Test
    fun current_account_is_marked_selected() {
        setContent(
            AccountSwitcherState(
                isLoading = false,
                accounts = accounts,
                currentAccountId = 2L,
            ),
        )

        composeTestRule.onNodeWithText("旅行账本").assertIsSelected()
    }

    /** 非当前账户不带选中语义 */
    @Test
    fun non_current_account_is_not_selected() {
        setContent(
            AccountSwitcherState(
                isLoading = false,
                accounts = accounts,
                currentAccountId = 1L,
            ),
        )

        composeTestRule.onNodeWithText("副卡").assertIsNotSelected()
    }
}