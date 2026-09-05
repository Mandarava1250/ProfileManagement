package com.example.personalassistant.core.notification

import javax.inject.Inject

/**
 * Phase 4a 占位实现 —— 永远返回 granted=true。
 *
 * Phase 5 默认绑定的是 [AndroidNotificationPermissionRequester]。
 * 本实现保留在类路径里，便于单测 fallback（DI 测试模块里 @Binds 它）。
 */
class FakeNotificationPermissionRequester @Inject constructor() : NotificationPermissionRequester {
    override suspend fun isGranted(): Boolean = true
    override suspend fun request(): Boolean = true
}
