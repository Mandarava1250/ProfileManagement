package com.example.personalassistant.core.notification

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
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
 */
@Composable
fun NotificationPermissionGate(
    requester: AndroidNotificationPermissionRequester = hiltViewModel<NotificationPermissionGateViewModel>().requester,
    permissionRepository: PermissionRepository = hiltViewModel<NotificationPermissionGateViewModel>().permissionRepository,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
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
        // 触发系统弹窗
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        val granted = requester.request(bridge)
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
 * 通知权限引导 ViewModel —— 用 @HiltViewModel 让 [NotificationPermissionGate] 直接拿依赖。
 *
 * 不放业务逻辑，只放依赖注入，gate 本身用 Composable 控制流程。
 */
@HiltViewModel
class NotificationPermissionGateViewModel @Inject constructor(
    val requester: AndroidNotificationPermissionRequester,
    val permissionRepository: PermissionRepository,
) : ViewModel()