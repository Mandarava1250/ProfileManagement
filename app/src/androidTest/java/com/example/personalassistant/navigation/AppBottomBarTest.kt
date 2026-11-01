package com.example.personalassistant.navigation

import androidx.activity.ComponentActivity
import com.example.personalassistant.R
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Phase 14 阶段七 7.2：底部导航栏（4 个 Tab + 「更多」）的 Compose UI 测试。
 *
 * 底栏是全局导航的骨架 —— 少一个 Tab、标签被截断、「更多」点不动，
 * 都会让用户「找不到功能」。这里锁住：
 * - 4 个功能 Tab 的文字标签**都渲染出来**（Phase 14 改成 alwaysShowLabel）
 * - 第 5 位「更多」也在
 * - 点「更多」触发回调
 *
 * ⚠️ 方法名必须是英文（androidTest 会被 D8 打包成 dex，方法名含空格会炸）。
 */
class AppBottomBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(res: Int): String = composeTestRule.activity.getString(res)

    /** 4 个 Tab 的标签 + 「更多」全部显示 */
    @Test
    fun shows_four_tabs_and_more_entry() {
        composeTestRule.setContent {
            AppBottomBar(
                navController = rememberNavController(),
                currentRoute = null,
                onMoreClick = {},
            )
        }

        BottomTab.entries.forEach { tab ->
            composeTestRule.onNodeWithText(text(tab.labelRes)).assertIsDisplayed()
        }
        composeTestRule.onNodeWithText(text(R.string.tab_more)).assertIsDisplayed()
    }

    /** 点「更多」→ 触发 onMoreClick */
    @Test
    fun click_more_triggers_more_callback() {
        var moreClicks = 0
        composeTestRule.setContent {
            AppBottomBar(
                navController = rememberNavController(),
                currentRoute = null,
                onMoreClick = { moreClicks++ },
            )
        }

        composeTestRule.onNodeWithText(text(R.string.tab_more)).performClick()

        assertEquals("点「更多」应触发 onMoreClick", 1, moreClicks)
    }
}
