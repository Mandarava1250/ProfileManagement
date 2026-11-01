package com.example.personalassistant.navigation

import androidx.activity.ComponentActivity
import com.example.personalassistant.R
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Phase 14 阶段七 7.2：「更多」面板的 Compose UI 测试。
 *
 * 底栏第 5 位是「更多」，点开后在底栏上方弹出面板，里面放「我」和「设置」。
 * 这条路径之前只有 JVM 单测覆盖了 route 白名单，**面板本身能不能点、点了跳哪**
 * 一直没有自动化验证 —— 这里补上。
 *
 * 文案一律从 string resource 取，不写死中文：万一「我」改成「我的」，
 * 测试应该跟着变，而不是红一片。
 */
class MoreMenuTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(res: Int): String = composeTestRule.activity.getString(res)

    /** 面板里两项都在 */
    @Test
    fun shows_me_and_settings_entries() {
        composeTestRule.setContent { MoreMenu(onNavigate = {}) }

        composeTestRule.onNodeWithText(text(R.string.tab_me)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.tab_settings)).assertIsDisplayed()
    }

    /** 点「我」→ 回调拿到 Me 的 route */
    @Test
    fun click_me_calls_navigate_with_me_route() {
        var navigated: String? = null
        composeTestRule.setContent { MoreMenu(onNavigate = { navigated = it }) }

        composeTestRule.onNodeWithText(text(R.string.tab_me)).performClick()

        assertEquals(Screen.Me.route, navigated)
    }

    /** 点「设置」→ 回调拿到 Settings 的 route */
    @Test
    fun click_settings_calls_navigate_with_settings_route() {
        var navigated: String? = null
        composeTestRule.setContent { MoreMenu(onNavigate = { navigated = it }) }

        composeTestRule.onNodeWithText(text(R.string.tab_settings)).performClick()

        assertEquals(Screen.Settings.route, navigated)
    }
}
