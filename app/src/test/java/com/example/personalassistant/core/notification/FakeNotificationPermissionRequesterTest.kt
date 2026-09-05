package com.example.personalassistant.core.notification

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5b：FakeNotificationPermissionRequester 单测。
 *
 * 重点验证：
 * - 默认行为（granted=true）保持兼容 Phase 4a
 * - 可配置 granted → isGranted / request 跟着变
 * - requestReturns 单独覆盖（测「已授权但 request 返回 false」异常场景）
 * - isGrantedCalls / requestCalls 计数正确
 */
class FakeNotificationPermissionRequesterTest {

    @Test
    fun `default granted is true and isGranted returns true without touching state`() = runTest {
        val fake = FakeNotificationPermissionRequester()
        assertTrue(fake.granted)
        assertTrue(fake.isGranted())
        assertEquals(1, fake.isGrantedCalls)
    }

    @Test
    fun `setting granted=false makes isGranted return false`() = runTest {
        val fake = FakeNotificationPermissionRequester()
        fake.granted = false
        assertFalse(fake.isGranted())
        assertEquals(1, fake.isGrantedCalls)
    }

    @Test
    fun `request returns granted value by default`() = runTest {
        val fake = FakeNotificationPermissionRequester()
        fake.granted = false
        assertFalse(fake.request())
        assertEquals(1, fake.requestCalls)

        fake.granted = true
        assertTrue(fake.request())
        assertEquals(2, fake.requestCalls)
    }

    @Test
    fun `requestReturns overrides granted when set`() = runTest {
        val fake = FakeNotificationPermissionRequester()
        fake.granted = true
        fake.requestReturns = false  // 异常场景：已授权但 request 返回 false
        assertFalse(fake.request())
        assertEquals(1, fake.requestCalls)
    }

    @Test
    fun `requestReturns null falls back to granted`() = runTest {
        val fake = FakeNotificationPermissionRequester()
        fake.granted = false
        fake.requestReturns = null
        assertFalse(fake.request())
        assertEquals(1, fake.requestCalls)
    }

    @Test
    fun `call counters accumulate across invocations`() = runTest {
        val fake = FakeNotificationPermissionRequester()
        fake.isGranted(); fake.isGranted(); fake.isGranted()
        fake.request(); fake.request()
        assertEquals(3, fake.isGrantedCalls)
        assertEquals(2, fake.requestCalls)
    }
}
