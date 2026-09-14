package com.example.personalassistant.navigation

import android.content.Context
import android.content.Intent
import com.example.personalassistant.MainActivity

/**
 * 通知 deeplink 路由（plan 8final §2.1 决策 v2）
 *
 * **为什么不引入自定义 URL scheme**：
 * - 会增加 Manifest `<intent-filter>` 多入口冲突风险
 * - 调试/冷启动参数解析边界繁琐
 * - 本 App 无外部来源（无分享、无浏览器跳转、无 NFC）→ scheme 无收益
 *
 * **为什么不直接 NavDeepLinkBuilder**：
 * - `NavDeepLinkBuilder` 需要 NavController 实例构造 PendingIntent
 * - BroadcastReceiver 触发时拿不到 NavController（Application 上下文）
 * - 强行注入会让架构变复杂，且不可测
 *
 * **采用的方案：显式 Intent + extras**：
 * - 3 个 Receiver 构造 `Intent(context, MainActivity::class.java)`
 * - extras 携带 `EXTRA_NAV_ROUTE`（已是真实路径，无占位符）+ `EXTRA_DEEPLINK_SOURCE`
 * - MainActivity 启动时（onCreate）或热启动时（onNewIntent）解析 → 写入 `pendingNavRoute`
 * - AppNavHost 用 `LaunchedEffect(pendingNavRoute)` 触发 `navController.navigate(route)`
 * - AppNavHost 内对 route 做白名单校验，防止恶意/损坏 Intent 跳到任意路径
 *
 * **验证维度**（plan v2 §2.2 要求）：冷启动 / 热启动 / 参数完整性 / 无 scheme 冲突
 *
 * **可测性设计**（plan v2 §2.5）：
 * - 解析逻辑 [parseRouteFromMap] / [parseSourceFromMap] 接受 `Map<String, String?>` 而不是 Intent
 * - Intent 工厂方法 [parseRoute] / [parseSource] 仅做 `intent.extras` → Map 适配
 * - 这样 JVM 单测可以直接测解析逻辑（绕过 Android Framework native 方法）
 *
 * 另注：使用 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` 让点击通知成为新栈顶并复用 Activity 实例。
 */
object DeepLinks {

    /** Receiver 写入 Intent extras 的 key：目标完整 route（参数已替换） */
    const val EXTRA_NAV_ROUTE = "nav_route"

    /** Receiver 写入 Intent extras 的 key：来源标识（log 用） */
    const val EXTRA_DEEPLINK_SOURCE = "deeplink_source"

    /** 深链来源标签 */
    const val SOURCE_TASK_RE = "task_reminder"
    const val SOURCE_EVENT_RE = "event_reminder"
    const val SOURCE_COURSE_RE = "course_reminder"

    // ==================== Intent 工厂（Receiver 用） ====================

    /**
     * 构造"打开任务详情"的显式 Intent。
     *
     * @param taskId 任务 id（必须 > 0，调用方校验）
     */
    fun taskDetailIntent(context: Context, taskId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_NAV_ROUTE, Screen.DetailTask.build(taskId))
            putExtra(EXTRA_DEEPLINK_SOURCE, SOURCE_TASK_RE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /** 构造"打开日程编辑"的显式 Intent */
    fun eventEditIntent(context: Context, eventId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_NAV_ROUTE, Screen.EditEvent.build(eventId))
            putExtra(EXTRA_DEEPLINK_SOURCE, SOURCE_EVENT_RE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /** 构造"打开课程编辑"的显式 Intent */
    fun courseEditIntent(context: Context, courseId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_NAV_ROUTE, Screen.EditCourse.build(courseId))
            putExtra(EXTRA_DEEPLINK_SOURCE, SOURCE_COURSE_RE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    // ==================== Intent 解析（MainActivity 用） ====================

    /**
     * 从 Intent 解析 nav route。
     * 返回 null 表示不是来自通知 deeplink 的启动（普通 LAUNCHER 启动）。
     *
     * **Android Framework 限制**：在 JVM 单测下，`Intent.extras` 是 native 调用，
     * 永远返回 null → 应改用 [parseRouteFromMap] 测试解析逻辑。
     */
    fun parseRoute(intent: Intent?): String? =
        intent?.let { parseRouteFromMap(toExtrasMap(it)) }

    /** 解析 deeplink 来源标签（log 用，可能为 null） */
    fun parseSource(intent: Intent?): String? =
        intent?.let { parseSourceFromMap(toExtrasMap(it)) }

    /**
     * 把 Intent extras 转成纯 String? Map（让解析逻辑脱离 Android Framework）。
     *
     * **JVM 单测兼容**：将 Android `Intent.getExtras().getString(key)` 这一系列
     * native 调用转换为单个隔离点。
     */
    private fun toExtrasMap(intent: Intent): Map<String, String?> {
        val map = mutableMapOf<String, String?>()
        val extras = intent.extras ?: return map
        for (key in extras.keySet()) {
            // extras.getString 可能返回 null → 保留 null
            map[key] = extras.getString(key)
        }
        return map
    }

    // ==================== 纯数据解析（JVM 单测可测） ====================

    /**
     * 解析 nav route。
     *
     * @param extras 模拟 Intent extras（key → String?）
     * @return 非空且非空白 → 命中；否则 → null（普通 LAUNCHER 启动）
     */
    fun parseRouteFromMap(extras: Map<String, String?>): String? =
        extras[EXTRA_NAV_ROUTE]?.takeIf { it.isNotBlank() }

    /** 解析 deeplink 来源标签（log 用，可能为 null） */
    fun parseSourceFromMap(extras: Map<String, String?>): String? =
        extras[EXTRA_DEEPLINK_SOURCE]?.takeIf { it.isNotBlank() }
}