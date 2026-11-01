package com.example.personalassistant.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * 统一的底栏 Tab 跳转 —— Phase 15 P0-1.1
 *
 * ## 为什么要有这个
 *
 * 之前底栏点击和页面内的跨页跳转用的是**两套不同的导航语义**：
 * - 底栏点击：`popUpTo(start) { saveState = true }` + `launchSingleTop` + `restoreState`
 * - 页面内跳转（比如日历「日」视图的预算横条跳去记账）：**裸 navigate**
 *
 * 裸 navigate 会把目标页**压到当前页之上**，栈变成 `[Calendar, Finance]`，
 * 与底栏的语义不一致 —— 用户在「今日剩余」跳去记账后，再想点底栏「日历」回来，
 * 走的却是一条和当初建栈时不一样的路径，于是出现「回不到日历」。
 *
 * 现在所有「切到某个底栏 Tab」的跳转都走这一个函数，栈行为只有一种。
 *
 * ## 与 deeplink 的区别
 *
 * 通知 deeplink 消费（见 [AppNavHost]）**故意不用**这个函数：
 * 它跳的是二级页（任务详情等），要保留底栏起始栈但不能 saveState
 * —— 否则二级页返回时底栏 Tab 的选中态会串。那条路径有它自己的注释说明。
 *
 * @param route 目标 route（通常是 `BottomTab.xxx.screen.route`）
 */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        // 回到起点，弹掉起点之上的所有页面 —— 栈里最多留两层（起点 + 目标 Tab）
        popUpTo(graph.findStartDestination().id) {
            saveState = true
            inclusive = false
        }
        // 目标已经在栈顶就不重复压入
        launchSingleTop = true
        // 恢复该 Tab 上次离开时的状态（滚动位置等）
        restoreState = true
    }
}
