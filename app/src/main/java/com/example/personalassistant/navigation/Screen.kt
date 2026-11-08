package com.example.personalassistant.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.personalassistant.R

/**
 * 路由集中管理：每个路由一个对象，避免散落的硬编码字符串
 */
sealed class Screen(val route: String) {
    data object Timetable : Screen("timetable")
    data object Tasks : Screen("tasks")
    data object Finance : Screen("finance")
    /**
     * 日历 · 日视图（日历日视图 + 选中日的日程流）
     *
     * 日历三视图都是**顶层**路由，日历 Tab 的落点。
     * 共享的 `CalendarViewModel` 挂在 NavHost（Activity）作用域上，
     * 所以切视图不会丢「选中日」，而返回键也不会把栈底弹空。
     */
    data object CalendarDay : Screen("calendar/day")

    /** 日历 · 周视图（周日历 + 工作日日程流 + 休息日日程流） */
    data object CalendarWeek : Screen("calendar/week")

    /** 日历 · 月视图（月日历网格 + 选中日的标签列表） */
    data object CalendarMonth : Screen("calendar/month")
    data object Settings : Screen("settings")
    /** Phase 12：底部 5 号 Tab「我」 */
    data object Me : Screen("me")

    /** 编辑页（按类型传参） */
    data object EditCourse : Screen("timetable/edit/{courseId}") {
        fun build(courseId: Long? = null) = "timetable/edit/${courseId ?: -1L}"
        const val ARG_COURSE_ID = "courseId"
    }

    data object EditTask : Screen("tasks/edit/{taskId}") {
        fun build(taskId: Long? = null) = "tasks/edit/${taskId ?: -1L}"
        const val ARG_TASK_ID = "taskId"
    }

    /**
     * Phase 6b：任务详情页（只读视图，含子任务编辑入口）
     *
     * - 任务主页 TasksTab 点击 TaskItem → 跳本页
     * - 本页右上角"编辑"按钮 → 跳 [EditTask]
     */
    data object DetailTask : Screen("tasks/detail/{taskId}") {
        fun build(taskId: Long? = null) = "tasks/detail/${taskId ?: -1L}"
        const val ARG_TASK_ID = "taskId"
    }

    data object EditEvent : Screen("calendar/edit/{eventId}") {
        fun build(eventId: Long? = null) = "calendar/edit/${eventId ?: -1L}"
        const val ARG_EVENT_ID = "eventId"
    }

    /**
     * Phase 13：分类管理（独立 route）
     *
     * 之前是 [com.example.personalassistant.feature.finance.FinanceScreen] 内的本地 state，
     * 设置页跳不过去 —— 提成独立 route 后，记账页和设置页都能进。
     */
    data object FinanceCategory : Screen("finance/category")

    /**
     * Phase 13：预算设置（独立 route）
     *
     * 同上：原本是 FinanceScreen 内的 showBudgetSheet 本地 state。
     */
    data object FinanceBudget : Screen("finance/budget")

    /** Phase 8 Sub-step 3：全局搜索（点击命中项后跳对应详情页） */
    data object GlobalSearch : Screen("globalsearch")

    /** Phase 8 Sub-step 4'（plan 8f'）：备份预览 + 三档策略导入 */
    data object BackupPreview : Screen("settings/backup-preview")

    /**
     * Phase 11：课表设置页（学期编辑 + 课时表编辑）
     *
     *  - 入口：TimetableScreen 右上角 ⚙️ 图标
     *  - 行为：Tab 切换"学期 / 课时表"，无 BottomBar（属于二级页）
     */
    data object TimetableSettings : Screen("timetable/settings")

    /**
     * Phase 12 GPA 规则：自定义学校绩点换算表
     *
     *  - 入口：「我」Tab 右上角 🎚（Tune）图标
     *  - 行为：编辑 scaleMax / scaleMin / 百分制→绩点 转换表，保存回 StudentProfile.gpaRule
     */
    data object EditGpaRule : Screen("me/gpa-rule")

    /**
     * Phase 14：外观设置（主题模式 / 主题色 / 背景）
     *
     *  - 入口：「我」页 → 外观
     *  - 行为：改完即时生效（写 DataStore → 顶层重新套色板）
     */
    data object Appearance : Screen("me/appearance")

    /**
     * Phase 23：习惯打卡页
     *
     *  - 入口：清单抽屉里的「习惯」项（ChecklistDrawer，Phase 17）
     *  - 行为：二级页，无 BottomBar
     */
    data object Habits : Screen("habits")
}

/**
 * 底部导航 4 个主 Tab（Phase 14：樱花粉 2.0 + 今日优先）
 *
 * Phase 14 之前的形态是 6 个 Tab（课表 / 任务 / 记账 / 日历 / 我 / 设置），
 * 纯图标、无文字 —— 6 个位置挤在 360dp 上，每个只剩图标，识别成本很高。
 *
 * 现在的形态：
 * - 4 个高频功能 Tab：日历 / 任务 / 课表 / 记账，**图标 + 文字标签常显**
 *   （4 项每格 90dp，11sp 中文标签绰绰有余，不用再猜图标是哪个）
 * - 第 5 个位置是「更多」，点开在底栏上方弹出面板，里面放「我」和「设置」
 * - 「我」「设置」的路由（[Screen.Me] / [Screen.Settings]）保留不动，
 *   deeplink 白名单不受影响
 *
 * 「今日」不占 Tab —— 它是日历 Tab 的「日」视图（见 CalendarViewModel.viewType）。
 */
enum class BottomTab(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Calendar(Screen.CalendarDay, R.string.tab_calendar, Icons.Rounded.CalendarMonth),
    Tasks(Screen.Tasks, R.string.tab_tasks, Icons.Rounded.Checklist),
    Timetable(Screen.Timetable, R.string.tab_timetable, Icons.Rounded.Today),
    Finance(Screen.Finance, R.string.tab_finance, Icons.Rounded.AccountBalanceWallet);
}
