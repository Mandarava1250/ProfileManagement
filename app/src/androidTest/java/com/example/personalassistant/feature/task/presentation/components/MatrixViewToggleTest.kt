package com.example.personalassistant.feature.task.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Phase 9c C2：`MatrixViewToggle`（列表 / 矩阵 两档切换）的 Compose UI 测试。
 *
 * ## 为什么单独测这个组件（补 Phase 8 的遗留缺口）
 *
 * Phase 8b'' 的 DEV_LOG 诚实登记过一条未覆盖项：
 * > **矩阵视图（`TaskViewMode.MATRIX`）自动化切换未跑通** ——
 * > 当时靠 `adb input tap` 点坐标，按钮位置随屏宽浮动，容易点偏；
 * > 安全做法需要 `uiautomator2`（本机无 pip）。
 *
 * 这个缺口在 Compose UI test 里其实**有正解**：`onNodeWithText()` 走的是
 * **语义树**而不是像素坐标 —— 无论屏宽怎么变、按钮画在哪，
 * 只要文本是「矩阵」就点得到。所以本测试用语义点击替代坐标点击，
 * 把 Phase 8 的遗留项真正补上。
 *
 * ## 覆盖的分支
 * - 列表模式下点「矩阵」→ onSelectMatrix 回调触发（进入矩阵）
 * - 矩阵模式下点「列表」→ onSelectList 回调触发（回到列表）
 * - 两种模式下两个 chip 都可点（不会因为选中态被禁用）
 *
 * ## ️ 为什么不断言「选中态」
 *
 * [ToggleChip] 是 `TextButton` + 条件背景色实现的**视觉**选中，
 * 没有挂 `selectable` / `selected` 语义 —— 因此 `assertIsSelected()` 不适用。
 * 硬要断言只能去比较背景色，那是实现细节，不是行为契约。
 * 真正要保证的行为是「点击能触发对应回调」，本测试只锁这一条。
 *
 * ## ⚠️ 方法名必须是英文
 *
 * androidTest 的类会被 D8 打包成 dex，旧 DEX 格式不允许方法名含空格
 * （项目踩坑记录：`D8: Space characters in SimpleName ... not allowed`）。
 * 中文说明一律放注释里。
 */
class MatrixViewToggleTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        isMatrixMode: Boolean,
        onSelectList: () -> Unit = {},
        onSelectMatrix: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            Column {
                MatrixViewToggle(
                    isMatrixMode = isMatrixMode,
                    onSelectList = onSelectList,
                    onSelectMatrix = onSelectMatrix,
                )
            }
        }
    }

    // ===== 语义可见性 =====

    /** 两个档位 + 「视图」标签都应渲染出来 */
    @Test
    fun renders_both_toggle_chips_and_label() {
        setContent(isMatrixMode = false)

        composeTestRule.onNodeWithText("视图").assertIsDisplayed()
        composeTestRule.onNodeWithText("列表").assertIsDisplayed()
        composeTestRule.onNodeWithText("矩阵").assertIsDisplayed()
    }

    // ===== 点击行为（Phase 8 遗留缺口的核心） =====

    /** 列表模式下点「矩阵」→ 触发 onSelectMatrix（且不误触 onSelectList） */
    @Test
    fun click_matrix_chip_triggers_select_matrix_callback() {
        var listClicks = 0
        var matrixClicks = 0
        setContent(
            isMatrixMode = false,
            onSelectList = { listClicks++ },
            onSelectMatrix = { matrixClicks++ },
        )

        composeTestRule.onNodeWithText("矩阵").performClick()

        assertEquals("点「矩阵」应触发 onSelectMatrix", 1, matrixClicks)
        assertEquals("点「矩阵」不应触发 onSelectList", 0, listClicks)
    }

    /** 矩阵模式下点「列表」→ 触发 onSelectList（且不误触 onSelectMatrix） */
    @Test
    fun click_list_chip_triggers_select_list_callback() {
        var listClicks = 0
        var matrixClicks = 0
        setContent(
            isMatrixMode = true,
            onSelectList = { listClicks++ },
            onSelectMatrix = { matrixClicks++ },
        )

        composeTestRule.onNodeWithText("列表").performClick()

        assertEquals("点「列表」应触发 onSelectList", 1, listClicks)
        assertEquals("点「列表」不应触发 onSelectMatrix", 0, matrixClicks)
    }

    /** 连续来回切两轮：回调累计正确，且选中态不会把 chip 变成不可点 */
    @Test
    fun chips_remain_clickable_across_both_states() {
        var listClicks = 0
        var matrixClicks = 0
        setContent(
            isMatrixMode = false,
            onSelectList = { listClicks++ },
            onSelectMatrix = { matrixClicks++ },
        )

        composeTestRule.onNodeWithText("矩阵").performClick()
        composeTestRule.onNodeWithText("矩阵").performClick()
        composeTestRule.onNodeWithText("列表").performClick()

        assertEquals(2, matrixClicks)
        assertEquals(1, listClicks)
    }

    /** 矩阵模式下「矩阵」chip 依然可点（重复点同一档不崩、回调照常） */
    @Test
    fun selected_matrix_chip_is_still_clickable() {
        var matrixClicks = 0
        setContent(isMatrixMode = true, onSelectMatrix = { matrixClicks++ })

        composeTestRule.onNodeWithText("矩阵").performClick()

        assertEquals(1, matrixClicks)
    }
}