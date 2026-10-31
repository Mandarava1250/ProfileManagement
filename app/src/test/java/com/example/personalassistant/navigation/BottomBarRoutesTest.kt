package com.example.personalassistant.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 14 阶段七：底栏 route 白名单单测（纯 JVM）
 *
 * 被测对象：`navigation/AppNavHost.kt` 里的
 * [BOTTOM_BAR_ROUTES] / [isBottomBarRoute]。
 *
 * 为什么要改主源码：
 * 原来白名单是 `private val`，测试根本拿不到；
 * 现在提成 `internal` 并配一个 [isBottomBarRoute]，
 * 测试直接断言**同一份实现**，不用把白名单抄到测试里（抄的版本迟早漂移）。
 *
 * 覆盖：
 * - 6 条底栏 route（4 个 Tab + 「我」+ 「设置」）全部命中
 * - 白名单内容与预期完全一致（不多不少）
 * - 4 个 BottomTab 都是底栏 route
 * - 二级页面（任务详情 / 任务编辑 / 日程编辑 / 课程编辑 / 全局搜索 / 备份预览 /
 *   课表设置 / GPA 规则 / 外观 / 记账子页）**不是**底栏 route
 * - null / 空串 / 未知 route 一律返回 false
 */
class BottomBarRoutesTest {

    @Test
    fun `the six bottom bar routes all hit the whitelist`() {
        val expected = listOf(
            Screen.Calendar.route,
            Screen.Tasks.route,
            Screen.Timetable.route,
            Screen.Finance.route,
            Screen.Me.route,
            Screen.Settings.route,
        )
        expected.forEach { route ->
            assertTrue("底栏 route 应命中白名单：$route", isBottomBarRoute(route))
        }
    }

    @Test
    fun `whitelist contains exactly the six bottom bar routes`() {
        val expected = setOf(
            Screen.Calendar.route,
            Screen.Tasks.route,
            Screen.Timetable.route,
            Screen.Finance.route,
            Screen.Me.route,
            Screen.Settings.route,
        )
        assertEquals(6, BOTTOM_BAR_ROUTES.size)
        assertEquals(expected, BOTTOM_BAR_ROUTES)
    }

    @Test
    fun `every bottom tab screen is a bottom bar route`() {
        BottomTab.entries.forEach { tab ->
            assertTrue(
                "Tab 必须是底栏 route：${tab.screen.route}",
                isBottomBarRoute(tab.screen.route),
            )
        }
    }

    @Test
    fun `secondary pages are not bottom bar routes`() {
        val secondary = listOf(
            // 任务：详情 / 编辑
            Screen.DetailTask.route,
            Screen.DetailTask.build(1L),
            Screen.EditTask.route,
            Screen.EditTask.build(1L),
            // 日程 / 课程：编辑
            Screen.EditEvent.route,
            Screen.EditEvent.build(1L),
            Screen.EditCourse.route,
            Screen.EditCourse.build(1L),
            // 其它二级页
            Screen.GlobalSearch.route,
            Screen.BackupPreview.route,
            Screen.TimetableSettings.route,
            Screen.EditGpaRule.route,
            Screen.Appearance.route,
            Screen.FinanceCategory.route,
            Screen.FinanceBudget.route,
            Screen.AddTransaction.route,
            Screen.MonthlyBudget.route,
        )
        secondary.forEach { route ->
            assertFalse("二级页面不应显示底栏：$route", isBottomBarRoute(route))
        }
    }

    @Test
    fun `null blank and unknown routes are not bottom bar routes`() {
        assertFalse("null route 不应显示底栏", isBottomBarRoute(null))
        assertFalse("空串 route 不应显示底栏", isBottomBarRoute(""))
        assertFalse("空白 route 不应显示底栏", isBottomBarRoute("   "))
        assertFalse("未知 route 不应显示底栏", isBottomBarRoute("admin/dashboard"))
        assertFalse("带参数的主 route 不应命中", isBottomBarRoute("tasks?filter=1"))
    }
}
