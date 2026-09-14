package com.example.personalassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.core.notification.NotificationPermissionGate
import com.example.personalassistant.core.ui.theme.PersonalAssistantTheme
import com.example.personalassistant.feature.settings.theme.ThemeViewModel
import com.example.personalassistant.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 入口：所有页面通过 Compose Navigation 切换
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 读取设置中的主题偏好（mode + dynamicColor）
            val themeVm: ThemeViewModel = hiltViewModel()
            val themePreferences by themeVm.themePreferences.collectAsStateWithLifecycle()

            PersonalAssistantTheme(
                themeMode = themePreferences.themeMode,
                dynamicColor = themePreferences.dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Phase 8 末尾补完：通知权限引导（Android 13+ 首次启动触发系统弹窗）
                    NotificationPermissionGate {
                        AppNavHost()
                    }
                }
            }
        }
    }
}
