package com.example.personalassistant.feature.task.presentation.components

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.personalassistant.feature.task.domain.model.MatrixCell
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskMatrix
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import org.junit.Rule
import org.junit.Test

/**
 * Phase 9c C2：`TaskMatrixView` 双布局（2×2 / 纵向 5 区）的 Compose UI 测试。
 *
 * ## 与 Phase 8 T7 证据的关系
 *
 * Phase 8 T7 用 `adb shell wm size` 切屏宽 + uiautomator dump 拿了两种布局的 UI 树。
 * 那份证据证明「渲染不崩」，但**没锁住行为** —— 屏宽一改就查不到具体分支了。
 * 本测试把断点判定搬进 Compose 测试，用 `LocalConfiguration` 覆盖 `screenWidthDp`，
 * 直接断言「哪种宽度渲染哪些区域」。**不需要改设备分辨率，也不需要第二台 AVD。**
 *
 * ## 为什么能覆盖 screenWidthDp
 *
 * [TaskMatrixView] 的分支靠 `LocalConfiguration.current.screenWidthDp` 判定
 * （断点 [TaskMatrix.MATRIX_BREAKPOINT_DP] = 600）。`LocalConfiguration`
 * 是可 provide 的 CompositionLocal，所以测试里能直接伪造宽度。
 *
 * ## 踩坑 1：纵向布局是 LazyColumn，不能直接 assert 屏外节点
 *
 * 纵向布局（< 600dp）只有一个 [androidx.compose.foundation.lazy.LazyColumn]，
 * **惰性组合**：屏外的 item 根本不会进语义树。一开始直接
 * `onNodeWithText("消除").assertIsDisplayed()` 全部报
 * `Assert failed: The component is not displayed!` —— 不是布局坏了，
 * 是节点压根没被组合。正解是先滚动：
 * `onNode(hasScrollAction()).performScrollToNode(hasText("消除"))`。
 *
 * ## 踩坑 2（我自己的测试前提写错了）
 *
 * 「未排期超过 3 条 → 显示『查看全部 N 条』折叠」这套逻辑**只存在于
 * `UnscheduledSection`（2×2 布局专用）**。纵向布局是直接把
 * `matrix.tasksOf(region)` 铺进 LazyColumn，**没有折叠**。
 * 所以折叠相关断言必须放在**宽屏 2×2** 下测，放窄屏下必然失败 ——
 * 这不是生产缺陷，是测试写错了位置。已改正并保留此记录。
 *
 * ## 诚实边界
 *
 * 伪造 `screenWidthDp = 800` 时**真实窗口仍是手机宽度**，2×2 四格被压进窄屏渲染，
 * 部分内容可能被裁切。所以宽屏断言用 `assertExists()`（结构对不对），
 * 窄屏（真实宽度）才用 `assertIsDisplayed()`。
 * **真机宽屏人眼目测仍留 Phase 10**，与本轮口径一致。
 *
 * ## ⚠️ 方法名必须是英文
 *
 * androidTest 的类会被 D8 打包成 dex，旧 DEX 格式不允许方法名含空格。
 */
class TaskMatrixViewLayoutTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun task(id: Long, title: String, priority: TaskPriority = TaskPriority.MEDIUM) =
        Task(id = id, title = title, priority = priority)

    /** 4 格各 1 条 + 无未排期 */
    private fun matrixWithAllCells(): TaskMatrix = TaskMatrix(
        cells = mapOf(
            MatrixCell.CELL_0 to listOf(task(1L, "格0 任务")),
            MatrixCell.CELL_1 to listOf(task(2L, "格1 任务")),
            MatrixCell.CELL_2 to listOf(task(3L, "格2 任务")),
            MatrixCell.CELL_3 to listOf(task(4L, "格3 任务")),
        ),
        unscheduled = emptyList(),
    )

    private fun matrixWithUnscheduled(count: Int): TaskMatrix = TaskMatrix(
        cells = mapOf(
            MatrixCell.CELL_0 to emptyList(),
            MatrixCell.CELL_1 to emptyList(),
            MatrixCell.CELL_2 to emptyList(),
            MatrixCell.CELL_3 to emptyList(),
        ),
        unscheduled = (1..count).map { task(it.toLong(), "未排期 $it") },
    )

    /** 伪造屏幕宽度 dp，渲染 [TaskMatrixView] */
    private fun setContentWithWidth(widthDp: Int, matrix: TaskMatrix) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val fake = Configuration(base).apply { screenWidthDp = widthDp }
            CompositionLocalProvider(LocalConfiguration provides fake) {
                TaskMatrixView(
                    matrix = matrix,
                    displayPreferences = TaskDisplayPreferences(),
                    onToggleComplete = { _, _ -> },
                    onClickTask = { },
                    onDelete = { },
                )
            }
        }
    }

    /** 纵向布局下滚到指定文本（LazyColumn 惰性组合，屏外节点不存在） */
    private fun scrollToText(text: String) {
        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(text))
    }

    // ===== 纵向 5 区（< 600dp） =====

    /** 窄屏：5 个区域全部渲染（未排期非空 + 4 格），滚动逐段确认 */
    @Test
    fun narrow_width_renders_all_five_regions() {
        setContentWithWidth(widthDp = 400, matrix = matrixWithUnscheduled(count = 2))

        // 未排期区在最顶部，首屏可见
        composeTestRule.onNodeWithText("未排期").assertIsDisplayed()
        composeTestRule.onNodeWithText("未排期 1").assertIsDisplayed()

        // 4 格依次向下，滚动确认每个区域头都渲染出来
        scrollToText("立即执行")
        composeTestRule.onNodeWithText("立即执行").assertIsDisplayed()

        scrollToText("计划安排")
        composeTestRule.onNodeWithText("计划安排").assertIsDisplayed()

        scrollToText("委托/简化")
        composeTestRule.onNodeWithText("委托/简化").assertIsDisplayed()

        scrollToText("消除")
        composeTestRule.onNodeWithText("消除").assertIsDisplayed()
    }

    /** 窄屏 + 未排期为空：该区域整块省略（不显示空壳），4 格仍保留 */
    @Test
    fun narrow_width_hides_empty_unscheduled_section() {
        setContentWithWidth(widthDp = 400, matrix = matrixWithAllCells())

        composeTestRule.onNodeWithText("未排期").assertDoesNotExist()
        composeTestRule.onNodeWithText("立即执行").assertIsDisplayed()

        scrollToText("消除")
        composeTestRule.onNodeWithText("消除").assertIsDisplayed()
    }

    /** 窄屏 + 任务在格内：任务标题随区域渲染 */
    @Test
    fun narrow_width_renders_task_titles_in_cells() {
        setContentWithWidth(widthDp = 400, matrix = matrixWithAllCells())

        composeTestRule.onNodeWithText("格0 任务").assertIsDisplayed()

        scrollToText("消除")
        composeTestRule.onNodeWithText("格3 任务").assertIsDisplayed()
    }

    /** 窄屏 + 全空矩阵：4 格保留占位（「无任务 ♪」），未排期区省略 */
    @Test
    fun narrow_width_empty_matrix_keeps_cell_placeholders() {
        setContentWithWidth(widthDp = 400, matrix = matrixWithUnscheduled(count = 0))

        composeTestRule.onNodeWithText("未排期").assertDoesNotExist()
        // 首格占位可见
        composeTestRule.onNodeWithText("立即执行").assertIsDisplayed()
        // 同屏可见多个「无任务 ♪」（每空格子一个），取首个断言
        composeTestRule.onAllNodesWithText("无任务 ♪").onFirst().assertIsDisplayed()

        // 纵向是 LazyColumn：滚到最后一格，确认它同样渲染了占位（未因空而塌掉）
        scrollToText("消除")
        composeTestRule.onNodeWithText("消除").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("无任务 ♪").onFirst().assertIsDisplayed()
    }

    // ===== 2×2 网格（>= 600dp） =====

    /** 宽屏：走 2×2 网格，4 格区域头 + 格内任务都在 */
    @Test
    fun wide_width_renders_two_by_two_grid_cells() {
        setContentWithWidth(widthDp = 800, matrix = matrixWithAllCells())

        composeTestRule.onNodeWithText("立即执行").assertExists()
        composeTestRule.onNodeWithText("计划安排").assertExists()
        composeTestRule.onNodeWithText("委托/简化").assertExists()
        composeTestRule.onNodeWithText("消除").assertExists()
        composeTestRule.onNodeWithText("格0 任务").assertExists()
    }

    /** 宽屏 + 有未排期：未排期区独立显示在矩阵上方（不属于 2×2） */
    @Test
    fun wide_width_shows_unscheduled_above_matrix() {
        setContentWithWidth(widthDp = 800, matrix = matrixWithUnscheduled(count = 2))

        composeTestRule.onNodeWithText("未排期").assertExists()
        composeTestRule.onNodeWithText("未排期 1").assertExists()
        composeTestRule.onNodeWithText("立即执行").assertExists()
    }

    /** 宽屏 + 未排期为空：区域同样省略 */
    @Test
    fun wide_width_hides_empty_unscheduled_section() {
        setContentWithWidth(widthDp = 800, matrix = matrixWithAllCells())

        composeTestRule.onNodeWithText("未排期").assertDoesNotExist()
    }

    /**
     * 宽屏 + 未排期超预览上限（5 条 → 预览 3 条）：
     * 显示「查看全部 2 条」，点开后第 4、5 条出现且按钮变「收起」。
     *
     * 折叠逻辑属于 [UnscheduledSection]，**只在 2×2 布局生效**。
     */
    @Test
    fun wide_width_unscheduled_overflow_expands_on_click() {
        setContentWithWidth(widthDp = 800, matrix = matrixWithUnscheduled(count = 5))

        composeTestRule.onNodeWithText("查看全部 2 条").assertExists()
        composeTestRule.onNodeWithText("未排期 4").assertDoesNotExist()

        composeTestRule.onNodeWithText("查看全部 2 条").performClick()

        // 展开后按钮下移，伪造宽屏下可能落到物理视口外 → 断言存在性
        composeTestRule.onNodeWithText("收起").assertExists()
        composeTestRule.onNodeWithText("未排期 4").assertExists()
        composeTestRule.onNodeWithText("未排期 5").assertExists()
    }

    /** 宽屏 + 未排期恰好 3 条（= 预览上限）：不显示折叠按钮 */
    @Test
    fun wide_width_unscheduled_at_preview_limit_has_no_expand_button() {
        setContentWithWidth(widthDp = 800, matrix = matrixWithUnscheduled(count = 3))

        composeTestRule.onNodeWithText("未排期 3").assertExists()
        composeTestRule.onNodeWithText("查看全部 2 条").assertDoesNotExist()
    }

    // ===== 断点边界 =====

    /** 断点边界：599dp 走纵向（< 600），600dp 走网格（>= 600） */
    @Test
    fun breakpoint_boundary_599_is_vertical() {
        setContentWithWidth(widthDp = 599, matrix = matrixWithAllCells())

        // 纵向布局：单一 LazyColumn，必须滚动才能看到最后一格
        composeTestRule.onNode(hasScrollAction()).assertExists()
        scrollToText("消除")
        composeTestRule.onNodeWithText("消除").assertIsDisplayed()
    }

    /** 断点边界另一侧：600dp 走 2×2，四格同时在语义树里（无需滚动） */
    @Test
    fun breakpoint_boundary_600_is_grid() {
        setContentWithWidth(widthDp = 600, matrix = matrixWithAllCells())

        // 2×2：外层 Column 不滚动、四格共用同一屏
        composeTestRule.onNodeWithText("立即执行").assertExists()
        composeTestRule.onNodeWithText("消除").assertExists()
    }
}