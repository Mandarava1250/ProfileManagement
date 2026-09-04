package com.example.personalassistant.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
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

    /** 编辑页（按类型传参） */
    data object EditCourse : Screen("timetable/edit/{courseId}") {
        fun build(courseId: Long? = null) = "timetable/edit/${courseId ?: -1L}"
        const val ARG_COURSE_ID = "courseId"
    }

    data object EditTask : Screen("tasks/edit/{taskId}") {
        fun build(taskId: Long? = null) = "tasks/edit/${taskId ?: -1L}"
        const val ARG_TASK_ID = "taskId"
    }

    data object EditEvent : Screen("calendar/edit/{eventId}") {
        fun build(eventId: Long? = null) = "calendar/edit/${eventId ?: -1L}"
        const val ARG_EVENT_ID = "eventId"
    }

    data object AddTransaction : Screen("finance/add")
    data object MonthlyBudget : Screen("finance/budget")
}

/**
 * 底部导航 4 个主 Tab
 */
enum class BottomTab(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Timetable(Screen.Timetable, R.string.tab_timetable, Icons.Filled.Today),
    Tasks(Screen.Tasks, R.string.tab_tasks, Icons.Filled.Checklist),
    Finance(Screen.Finance, R.string.tab_finance, Icons.Filled.AccountBalanceWallet),
    Calendar(Screen.Calendar, R.string.tab_calendar, Icons.Filled.CalendarMonth);

    companion object {
        // 设置页不在 BottomBar，但在底部溢出菜单中可以触达
        val SETTINGS = Screen.Settings to Icons.Filled.Settings
    }
}
