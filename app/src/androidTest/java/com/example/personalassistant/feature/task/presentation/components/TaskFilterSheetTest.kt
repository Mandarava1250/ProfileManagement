package com.example.personalassistant.feature.task.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.personalassistant.R
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.TaskSort
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Phase 14 阶段七 7.2：任务页「筛选与排序」底部面板的 Compose UI 测试。
 *
 * 6.3b 把分类 / 标签 / 排序 / 视图四段从首屏折进了这个面板 ——
 * 折进去之后，面板「打不开」「四段不齐」「点排序没反应」都是用户直接可见的故障，
 * 之前没有自动化覆盖，这里补上。
 *
 * ## 为什么用槽位
 *
 * 标签区与视图切换由调用方传入（[TaskFilterSheet] 的 `tagContent` / `viewContent`），
 * 测试里塞两段占位文本，就能断言「这两段确实被渲染了」——
 * 既不用造标签数据，也不用拉起整个任务页。
 *
 * ⚠️ 方法名必须是英文（androidTest 会被 D8 打包成 dex，方法名含空格会炸）。
 */
class TaskFilterSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(res: Int): String = composeTestRule.activity.getString(res)

    private fun setContent(
        filter: FilterExpression = FilterExpression.All,
        sort: TaskSort = TaskSort.DEFAULT,
        onFilterChange: (FilterExpression) -> Unit = {},
        onSortSelected: (TaskSort) -> Unit = {},
    ) {
        composeTestRule.setContent {
            TaskFilterSheet(
                filter = filter,
                sort = sort,
                onFilterChange = onFilterChange,
                onSortSelected = onSortSelected,
                tagContent = { Text("TAG_SLOT") },
                viewContent = { Text("VIEW_SLOT") },
                onDismiss = {},
            )
        }
    }

    /** 四个小节标题都在 */
    @Test
    fun shows_four_section_titles() {
        setContent()

        composeTestRule.onNodeWithText(text(R.string.task_filter_category)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.task_filter_tag)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.task_filter_sort)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.task_filter_view)).assertIsDisplayed()
    }

    /** 槽位内容被渲染出来（标签区与视图切换确实挂上了） */
    @Test
    fun renders_tag_and_view_slots() {
        setContent()

        composeTestRule.onNodeWithText("TAG_SLOT").assertIsDisplayed()
        composeTestRule.onNodeWithText("VIEW_SLOT").assertIsDisplayed()
    }

    /** 点排序选项 → 回调拿到对应枚举 */
    @Test
    fun click_sort_option_triggers_sort_callback() {
        var selected: TaskSort? = null
        setContent(onSortSelected = { selected = it })

        composeTestRule.onNodeWithText("优先级 + 截止日期").performClick()

        assertEquals(TaskSort.PRIORITY_DUE_ASC, selected)
    }

    /** 分类区点「全部」→ 回调拿到 FilterExpression.All */
    @Test
    fun click_all_category_triggers_filter_callback() {
        var changed: FilterExpression? = null
        setContent(onFilterChange = { changed = it })

        composeTestRule.onNodeWithText(text(R.string.action_all)).performClick()

        assertEquals(FilterExpression.All, changed)
    }
}
