package com.example.personalassistant.core.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Phase 5 真实实现：通知权限申请。
 *
 * - [isGranted]：直接 checkSelfPermission
 * - [request]：通过 ActivityResultBridge 拿到结果
 *
 * 为什么不用 [androidx.activity.compose.rememberLauncherForActivityResult] 直接挂在 UI？
 * - ViewModel/UseCase 层不应该依赖 Compose 的 launcher
 * - 需要把请求结果「异步桥」出来，方便业务层在 ViewModel 里 await
 * - 实际 Compose UI 端只需把 ActivityResultBridge 实例传入 rememberLauncherForActivityResult，
 *   然后调 requester.request() 即可
 *
 * 用法：
 * ```
 * val bridge = remember { ActivityResultBridge() }
 * val requester: NotificationPermissionRequester = hiltViewModel<...>().requester
 * val launcher = rememberLauncherForActivityResult(
 *     ActivityResultContracts.RequestPermission(),
 *     bridge::set
 * )
 * LaunchedEffect(Unit) {
 *     val granted = requester.request(launcher)
 * }
 * ```
 */
@Singleton
class AndroidNotificationPermissionRequester @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationPermissionRequester {

    override suspend fun isGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 申请通知权限，结果由 [bridge] 桥接返回。
     *
     * @param bridge UI 侧用 rememberLauncherForActivityResult 注册后传入
     */
    suspend fun request(bridge: ActivityResultBridge): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        if (isGranted()) return true
        return suspendCancellableCoroutine { cont ->
            bridge.callback = { granted ->
                if (cont.isActive) cont.resume(granted)
            }
        }
    }

    // 兼容旧接口（Fake 风格）
    override suspend fun request(): Boolean = isGranted()
}

/**
 * Compose / View 层用这个 Bridge 把 launcher 结果回传给 AndroidNotificationPermissionRequester。
 *
 * 用法（Compose）：
 * ```
 * val bridge = remember { ActivityResultBridge() }
 * val launcher = rememberLauncherForActivityResult(
 *     contract = ActivityResultContracts.RequestPermission(),
 *     onResult = { granted -> bridge.set(granted) }
 * )
 * val requester: AndroidNotificationPermissionRequester = ...
 * LaunchedEffect(Unit) {
 *     val granted = requester.request(bridge)
 * }
 * ```
 */
class ActivityResultBridge {
    @Volatile
    var callback: ((Boolean) -> Unit)? = null

    fun set(granted: Boolean) {
        callback?.invoke(granted)
        callback = null
    }
}
