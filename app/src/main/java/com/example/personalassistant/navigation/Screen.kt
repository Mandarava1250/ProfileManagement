package com.example.personalassistant.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.personalassistant.R

/**
 * 路由集中管理：每个路由一个对象，避免散落的硬编码字符串
 */
sealed class Screen(val route: String) {
    data object Timetable : Screen("timetable")
    data object Tasks : Screen("tasks")
    data object Finance : Screen("finance")
    data object Calendar : Screen("calendar")
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

    data object AddTransaction : Screen("finance/add")
    data object MonthlyBudget : Screen("finance/budget")

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
}

/**
 * 底部导航 6 个主 Tab
 *
 * Phase 13：把「设置」从「我」页的隐式入口提升为底部第 6 个 Tab，
 * 设置页内部按 课表 / 任务 / 记账 / 日历 / 我的 分组。
 *
 * **一律只显示图标**：6 个 tab 挤在窄屏上，文字标签会把每个位置压得极窄、
 * 字号也跟着缩。图标本身已经能区分（今天 / 清单 / 钱包 / 月历 / 人 / 齿轮），
 * 无障碍名称由图标 contentDescription 承担（见 AppNavHost 的 AppBottomBar）。
 */
enum class BottomTab(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Timetable(Screen.Timetable, R.string.tab_timetable, Icons.Filled.Today),
    Tasks(Screen.Tasks, R.string.tab_tasks, Icons.Filled.Checklist),
    Finance(Screen.Finance, R.string.tab_finance, Icons.Filled.AccountBalanceWallet),
    Calendar(Screen.Calendar, R.string.tab_calendar, Icons.Filled.CalendarMonth),
    Me(Screen.Me, R.string.tab_me, Icons.Filled.Person),
    Settings(Screen.Settings, R.string.tab_settings, Icons.Filled.Settings);
}
