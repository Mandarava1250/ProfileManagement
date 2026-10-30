package com.example.personalassistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.core.notification.NotificationPermissionGate
import com.example.personalassistant.core.ui.theme.PersonalAssistantTheme
import com.example.personalassistant.feature.settings.theme.ThemeViewModel
import com.example.personalassistant.navigation.AppNavHost
import com.example.personalassistant.navigation.DeepLinks
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 入口：所有页面通过 Compose Navigation 切换
 *
 * **通知 deeplink 消费点（plan 8final §2.1 v2）**：
 * - onCreate 时：消费 `intent` 中的 [DeepLinks.EXTRA_NAV_ROUTE]（冷启动场景）
 * - onNewIntent 时：消费新 `intent`（热启动 / App 在前台时点击通知）
 * - 把消费后的 route 写入 [pendingNavRoute]（MutableState）
 * - 由 [AppNavHost] 用 `LaunchedEffect(pendingNavRoute)` 触发 `navController.navigate`
 *
 * **必须 `setIntent(intent)`**：
 * - Android onNewIntent 默认不改 Activity.intent，需手动 set 后下游才看得到新 intent
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 当前待消费的导航路由（null = 非 deeplink 启动） */
    private val pendingNavRoute: MutableState<String?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷启动消费 intent（LAUNCHER 启动时为 null）
        pendingNavRoute.value = DeepLinks.parseRoute(intent)

        enableEdgeToEdge()
        setContent {
            // 读取设置中的主题偏好（mode + dynamicColor）
            val themeVm: ThemeViewModel = hiltViewModel()
            val themePreferences by themeVm.themePreferences.collectAsStateWithLifecycle()

            PersonalAssistantTheme(
                themeMode = themePreferences.themeMode,
                dynamicColor = themePreferences.dynamicColor,
                primaryColor = themePreferences.primaryColor,
                backgroundStyle = themePreferences.backgroundStyle,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Phase 8 末尾补完：通知权限引导（Android 13+ 首次启动触发系统弹窗）
                    NotificationPermissionGate {
                        AppNavHost(pendingNavRoute = pendingNavRoute)
                    }
                }
            }
        }
    }

    /**
     * 热启动：App 已在内存中，点通知后系统回调此方法
     *
     * 注意：
     * - onNewIntent 默认 **不会** 修改 Activity.intent，必须 setIntent(intent) 让下游 getIntent() 拿到新值
     * - pendingNavRoute 是 MutableState，写入后 Compose 自动 recomposition → AppNavHost LaunchedEffect 触发
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = DeepLinks.parseRoute(intent)
        if (route != null) {
            pendingNavRoute.value = route
        }
    }
}