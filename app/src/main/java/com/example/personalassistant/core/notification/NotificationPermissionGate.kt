package com.example.personalassistant.core.notification

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 通知权限引导 Composable —— Phase 8 末尾补完。
 *
 * 使用方式（在 MainActivity 根节点包裹业务 UI）：
 * ```kotlin
 * NotificationPermissionGate {
 *     AppNavHost()
 * }
 * ```
 *
 * 行为：
 * 1. 应用启动时（[LaunchedEffect] Unit）读取 [PermissionRepository.notificationPermissionAsked]
 * 2. 若已询问过 → 跳过弹窗，直接渲染 [content]
 * 3. 若未询问过且未授权 → 弹系统权限对话框
 * 4. 收到结果（不论 granted / denied）→ 写回"已询问过"，避免下次再弹
 *
 * 注：不需要拦截 [content] 的渲染。Dialog 弹起时底层 UI 仍可见，符合 Android 规范。
 *
 * ★ 首帧顺序是硬约束（实测踩过）：[launcher] 必须在 [content] 的首帧提交之后再 launch。
 * 否则 GrantPermissionsActivity 会在首帧之前把本 Activity 切到 paused，
 * WindowManager 永远收不到 Displayed，用户看到的是一整张 Surface 白底（白屏），
 * 直到他点掉权限框为止。[awaitFirstDraw] 就是用来保证这个顺序的。
 */
@Composable
fun NotificationPermissionGate(
    requester: AndroidNotificationPermissionRequester = hiltViewModel<NotificationPermissionGateViewModel>().requester,
    permissionRepository: PermissionRepository = hiltViewModel<NotificationPermissionGateViewModel>().permissionRepository,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val bridge = remember { ActivityResultBridge() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bridge.set(granted)
    }

    LaunchedEffect(Unit) {
        val alreadyAsked = permissionRepository.notificationPermissionAsked.first()
        if (alreadyAsked) return@LaunchedEffect
        if (requester.isGranted()) {
            // 已授权（可能用户在系统设置里改过）→ 标记已询问，避免每次启动都走逻辑
            permissionRepository.setNotificationPermissionAsked(true)
            return@LaunchedEffect
        }
        // 先让业务 UI 出首帧，再触发系统弹窗（见类注释 ★）
        awaitFirstDraw(view)
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        val granted = withTimeoutOrNull(AWAIT_RESULT_TIMEOUT_MS) { requester.request(bridge) }
        if (granted == null) {
            // 没等到结果（回调丢失 / 进程被切走）：保持"未询问"，下次启动重试，
            // 也不跳系统设置页，避免把用户推到陌生界面。
            return@LaunchedEffect
        }
        // 无论结果如何，都标记"已询问"，避免重复弹窗打扰用户
        permissionRepository.setNotificationPermissionAsked(true)
        // 用户拒绝：跳系统设置页（用 shouldShowRequestPermissionRationale 进一步判定）
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context as? android.app.Activity
            if (activity != null) {
                // rationale=true → 用户明确拒绝了（应该教育用户）；rationale=false → 首次询问或"不再询问"
                // 只有 rationale=false 且已拒绝时，跳系统设置才合理
                val rationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
                if (!rationale) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    content()
}

/**
 * 等首帧的兜底上限：静态界面可能不再触发第二次 onDraw，超时即视为已绘制。
 *
 * 取值依据：aosp_atd（GPU 关闭 + 软件渲染）上冷启动 Displayed 实测 +7.9s，
 * 400ms 极易误判超时并退化成「弹窗抢首帧」的白屏老路。给 2s 缓冲，
 * 正常设备 listener 会先触发、不受此值影响。
 */
private const val FIRST_DRAW_TIMEOUT_MS = 2000L

/** 等权限结果的兜底上限，避免协程无期限挂起。 */
private const val AWAIT_RESULT_TIMEOUT_MS = 120_000L

/**
 * 等 [view] 所在 Window 完成首帧绘制；最多等 [FIRST_DRAW_TIMEOUT_MS]。
 *
 * [LaunchedEffect] 的协程体在组合应用之后才派发，有可能已经错过首帧的 onDraw，
 * 因此这里必须带超时兜底：等不到就继续走，绝不能把权限请求永久卡死。
 */
private suspend fun awaitFirstDraw(
    view: View,
    timeoutMs: Long = FIRST_DRAW_TIMEOUT_MS,
) {
    withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val observer: ViewTreeObserver = view.viewTreeObserver
            val listener = object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    // onDraw 回调内直接改监听器列表会抛异常，统一 post 出去摘除
                    view.post {
                        if (observer.isAlive) observer.removeOnDrawListener(this)
                    }
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            observer.addOnDrawListener(listener)
            cont.invokeOnCancellation {
                view.post {
                    if (observer.isAlive) observer.removeOnDrawListener(listener)
                }
            }
        }
    }
}

/**
 * 通知权限引导 ViewModel —— 用 @HiltViewModel 让 [NotificationPermissionGate] 直接拿依赖。
 *
 * 不放业务逻辑，只放依赖注入，gate 本身用 Composable 控制流程。
 */
@HiltViewModel
class NotificationPermissionGateViewModel @Inject constructor(
    val requester: AndroidNotificationPermissionRequester,
    val permissionRepository: PermissionRepository,
) : ViewModel()