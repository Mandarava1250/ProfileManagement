package com.example.personalassistant.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 收尾（plan 8final §2.1）DeepLinks 单测（纯 JVM）
 *
 * **可测性技巧**：
 * - 走 [DeepLinks.parseRouteFromMap] / [parseSourceFromMap] 路径（接受 `Map<String, String?>`）
 * - 避开 `Intent.getExtras()` 这种 Android Framework native 调用（JVM 单测下永远返回 null）
 * - Intent 适配路径 [DeepLinks.parseRoute] 只能在 androidTest 中跑
 *
 * **覆盖维度**（plan v2 §2.2）：
 * - 冷启动场景：null extras / 无 nav_route extras / 有 nav_route extras
 * - 热启动场景：第二次 setExtras 后 parseRoute 拿到新值（用 map 替换模拟）
 * - 参数完整性：taskId / eventId / courseId 正确编码
 * - route 白名单校验契约
 */
class DeepLinksTest {

    // ===== parseRouteFromMap =====

    @Test
    fun `parseRouteFromMap returns null for empty map`() {
        assertNull(DeepLinks.parseRouteFromMap(emptyMap()))
    }

    @Test
    fun `parseRouteFromMap returns null when EXTRA_NAV_ROUTE missing`() {
        val extras = mapOf<String, String?>("foo" to "bar")
        assertNull(DeepLinks.parseRouteFromMap(extras))
    }

    @Test
    fun `parseRouteFromMap returns null when EXTRA_NAV_ROUTE is null`() {
        val extras = mapOf<String, String?>(DeepLinks.EXTRA_NAV_ROUTE to null)
        assertNull(DeepLinks.parseRouteFromMap(extras))
    }

    @Test
    fun `parseRouteFromMap returns null when EXTRA_NAV_ROUTE is blank`() {
        val extras = mapOf<String, String?>(
            DeepLinks.EXTRA_NAV_ROUTE to "   ",
            DeepLinks.EXTRA_NAV_ROUTE + "_other" to "task_reminder",
        )
        assertNull(DeepLinks.parseRouteFromMap(extras))
    }

    @Test
    fun `parseRouteFromMap returns the route when present`() {
        val extras = mapOf<String, String?>(DeepLinks.EXTRA_NAV_ROUTE to "tasks/detail/42")
        assertEquals("tasks/detail/42", DeepLinks.parseRouteFromMap(extras))
    }

    @Test
    fun `parseRouteFromMap handles route with negative id placeholder`() {
        // 模拟 build(null) → "tasks/detail/-1"
        val extras = mapOf<String, String?>(DeepLinks.EXTRA_NAV_ROUTE to "tasks/detail/-1")
        assertEquals("tasks/detail/-1", DeepLinks.parseRouteFromMap(extras))
    }

    @Test
    fun `parseRouteFromMap ignores other keys`() {
        val extras = mapOf<String, String?>(
            "unrelated_key" to "garbage",
            DeepLinks.EXTRA_NAV_ROUTE to "calendar/edit/100",
        )
        assertEquals("calendar/edit/100", DeepLinks.parseRouteFromMap(extras))
    }

    // ===== parseSourceFromMap =====

    @Test
    fun `parseSourceFromMap returns null when missing`() {
        assertNull(DeepLinks.parseSourceFromMap(emptyMap()))
    }

    @Test
    fun `parseSourceFromMap returns task_reminder label`() {
        val extras = mapOf<String, String?>(
            DeepLinks.EXTRA_DEEPLINK_SOURCE to DeepLinks.SOURCE_TASK_RE,
        )
        assertEquals(DeepLinks.SOURCE_TASK_RE, DeepLinks.parseSourceFromMap(extras))
    }

    @Test
    fun `parseSourceFromMap returns event_reminder label`() {
        val extras = mapOf<String, String?>(
            DeepLinks.EXTRA_DEEPLINK_SOURCE to DeepLinks.SOURCE_EVENT_RE,
        )
        assertEquals(DeepLinks.SOURCE_EVENT_RE, DeepLinks.parseSourceFromMap(extras))
    }

    @Test
    fun `parseSourceFromMap returns course_reminder label`() {
        val extras = mapOf<String, String?>(
            DeepLinks.EXTRA_DEEPLINK_SOURCE to DeepLinks.SOURCE_COURSE_RE,
        )
        assertEquals(DeepLinks.SOURCE_COURSE_RE, DeepLinks.parseSourceFromMap(extras))
    }

    @Test
    fun `parseSourceFromMap returns null for blank value`() {
        val extras = mapOf<String, String?>(DeepLinks.EXTRA_DEEPLINK_SOURCE to "  ")
        assertNull(DeepLinks.parseSourceFromMap(extras))
    }

    // ===== 参数完整性（工厂方法逻辑校验）=====

    @Test
    fun `taskDetail extras contain correct route and source`() {
        val taskId = 42L
        val extras = mapOf<String, String?>(
            DeepLinks.EXTRA_NAV_ROUTE to Screen.DetailTask.build(taskId),
            DeepLinks.EXTRA_DEEPLINK_SOURCE to DeepLinks.SOURCE_TASK_RE,
        )
        assertEquals("tasks/detail/42", DeepLinks.parseRouteFromMap(extras))
        assertEquals(DeepLinks.SOURCE_TASK_RE, DeepLinks.parseSourceFromMap(extras))
    }

    @Test
    fun `eventEdit extras contain correct route and source`() {
        val eventId = 100L
        val extras = mapOf<String, String?>(
            DeepLinks.EXTRA_NAV_ROUTE to Screen.EditEvent.build(eventId),
            DeepLinks.EXTRA_DEEPLINK_SOURCE to DeepLinks.SOURCE_EVENT_RE,
        )
        assertEquals("calendar/edit/100", DeepLinks.parseRouteFromMap(extras))
        assertEquals(DeepLinks.SOURCE_EVENT_RE, DeepLinks.parseSourceFromMap(extras))
    }

    @Test
    fun `courseEdit extras contain correct route and source`() {
        val courseId = 7L
        val extras = mapOf<String, String?>(
            DeepLinks.EXTRA_NAV_ROUTE to Screen.EditCourse.build(courseId),
            DeepLinks.EXTRA_DEEPLINK_SOURCE to DeepLinks.SOURCE_COURSE_RE,
        )
        assertEquals("timetable/edit/7", DeepLinks.parseRouteFromMap(extras))
        assertEquals(DeepLinks.SOURCE_COURSE_RE, DeepLinks.parseSourceFromMap(extras))
    }

    // ===== Screen.build 边界 =====

    @Test
    fun `Screen build with null id produces negative id placeholder`() {
        assertEquals("tasks/detail/-1", Screen.DetailTask.build(null))
        assertEquals("tasks/edit/-1", Screen.EditTask.build(null))
        assertEquals("calendar/edit/-1", Screen.EditEvent.build(null))
        assertEquals("timetable/edit/-1", Screen.EditCourse.build(null))
    }

    @Test
    fun `Screen build with positive id produces correct route`() {
        assertEquals("tasks/detail/1", Screen.DetailTask.build(1L))
        assertEquals("tasks/edit/999", Screen.EditTask.build(999L))
        assertEquals("calendar/edit/123456789", Screen.EditEvent.build(123_456_789L))
        assertEquals("timetable/edit/42", Screen.EditCourse.build(42L))
    }

    // ===== 路由白名单（AppNavHost.isKnownDeepLinkRoute 行为契约）=====

    @Test
    fun `whitelisted routes pass basic prefix validation`() {
        val allowed = listOf(
            "timetable",
            "tasks",
            "finance",
            "calendar",
            "settings",
            "globalsearch",
            "settings/backup-preview",
            "timetable/edit/1",
            "tasks/edit/2",
            "tasks/detail/3",
            "calendar/edit/4",
        )
        allowed.forEach { route ->
            assertTrue(
                "合法路由应被白名单接受：$route",
                matchesWhitelist(route),
            )
        }
    }

    @Test
    fun `unknown routes are rejected`() {
        // 注：白名单仅检查前缀形态（不验 id 合法性）；id 合法性由 NavType.LongType 在 navigate 时验证
        val rejected = listOf(
            "admin/dashboard",       //  // 完全陌生
            "settings/foo/bar",     // // 嵌套乱写（settings 下没有 /foo/bar）
            "",
        )
        rejected.forEach { route ->
            assertFalse(
                "未知路由应被白名单拒绝：'$route'",
                matchesWhitelist(route),
            )
        }
    }

    /**
     * 与 AppNavHost.isKnownDeepLinkRoute 保持同源实现
     * **警告**：一旦 AppNavHost 的白名单逻辑变化，此处必须同步
     */
    private fun matchesWhitelist(route: String): Boolean {
        if (route.isBlank()) return false
        return route == Screen.Timetable.route ||
            route == Screen.Tasks.route ||
            route == Screen.Finance.route ||
            route == Screen.Calendar.route ||
            route == Screen.Settings.route ||
            route == Screen.GlobalSearch.route ||
            route == Screen.BackupPreview.route ||
            route.startsWith("timetable/edit/") ||
            route.startsWith("tasks/edit/") ||
            route.startsWith("tasks/detail/") ||
            route.startsWith("calendar/edit/")
    }

    // ===== 热启动模拟 =====

    @Test
    fun `hot start scenario - replacing extras replaces parsed route`() {
        // 模拟 MainActivity.onNewIntent：MutableState 用新 route 替换旧 route
        var extras: Map<String, String?> = mapOf(
            DeepLinks.EXTRA_NAV_ROUTE to "tasks/detail/1",
            DeepLinks.EXTRA_DEEPLINK_SOURCE to DeepLinks.SOURCE_TASK_RE,
        )
        assertEquals("tasks/detail/1", DeepLinks.parseRouteFromMap(extras))
        assertEquals(DeepLinks.SOURCE_TASK_RE, DeepLinks.parseSourceFromMap(extras))

        // onNewIntent：替换为新 route
        extras = mapOf(
            DeepLinks.EXTRA_NAV_ROUTE to "calendar/edit/2",
            DeepLinks.EXTRA_DEEPLINK_SOURCE to DeepLinks.SOURCE_EVENT_RE,
        )
        assertEquals("calendar/edit/2", DeepLinks.parseRouteFromMap(extras))
        assertEquals(DeepLinks.SOURCE_EVENT_RE, DeepLinks.parseSourceFromMap(extras))
    }

    @Test
    fun `hot start scenario - clearing extras returns null`() {
        // 模拟：消费完 pendingNavRoute 后把它清空（AppNavHost 里 navigate 后做）
        var extras: Map<String, String?> = mapOf(
            DeepLinks.EXTRA_NAV_ROUTE to "tasks/detail/1",
        )
        assertEquals("tasks/detail/1", DeepLinks.parseRouteFromMap(extras))

        // AppNavHost 消费后把 pendingNavRoute 置 null → 下次 LaunchedEffect 不触发
        extras = emptyMap()
        assertNull(DeepLinks.parseRouteFromMap(extras))
    }
}