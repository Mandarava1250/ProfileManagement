package com.example.personalassistant.core.notification

/**
 * Phase 4a 占位实现 —— 永远返回 granted=true。
 *
 * 真正接 Android 13+ POST_NOTIFICATIONS 时，替换为：
 *   AndroidNotificationPermissionRequester(activity: ComponentActivity)
 */
class FakeNotificationPermissionRequester : NotificationPermissionRequester {
    override suspend fun isGranted(): Boolean = true
    override suspend fun request(): Boolean = true
}
