package com.example.personalassistant.core.notification

import javax.inject.Inject

/**
 * 测试用 Fake —— 可配置 granted 行为。
 *
 * Phase 4a：永远 granted=true（占位）
 * Phase 5b：暴露 [granted] / [requestReturns] / [requestCalls] 字段供单测场景化
 *
 * 默认 `granted = true` 保证 Phase 5 已通过的 85+ 单测无需改动。
 * 当 `granted = false` 时模拟「未授权」，单测可验证是否触发 shouldRequest。
 *
 * DI 仍然绑到 [AndroidNotificationPermissionRequester]；测试 module 里 @Binds 它。
 */
class FakeNotificationPermissionRequester @Inject constructor() : NotificationPermissionRequester {

    /** 当前是否已授权（控制 isGranted() 返回值）。默认 true。 */
    var granted: Boolean = true

    /** request() 的返回值（默认跟 granted 一致，但可独立覆盖测异常场景）。 */
    var requestReturns: Boolean? = null

    /** isGranted() 调用次数，便于断言「VM 没调过 isGranted」。 */
    var isGrantedCalls: Int = 0
        private set

    /** request() 调用次数。 */
    var requestCalls: Int = 0
        private set

    override suspend fun isGranted(): Boolean {
        isGrantedCalls++
        return granted
    }

    override suspend fun request(): Boolean {
        requestCalls++
        return requestReturns ?: granted
    }
}
