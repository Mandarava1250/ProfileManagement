package com.example.personalassistant.core.notification

/**
 * 通知权限申请抽象 —— 真实实现会走 ActivityResultContracts.RequestPermission。
 *
 * Phase 4a: 仅留接口 + Fake 默认实现（auto-grant），不挂 Manifest / Activity 调用链。
 * Phase 5 真正接 Android 13+ POST_NOTIFICATIONS 时，新增 AndroidNotificationPermissionRequester。
 */
interface NotificationPermissionRequester {
    /** 当前是否已授权通知权限（无需触发系统弹窗） */
    suspend fun isGranted(): Boolean

    /** 申请一次通知权限，返回用户最终选择（granted=true 表示同意） */
    suspend fun request(): Boolean
}
