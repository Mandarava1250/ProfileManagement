package com.example.personalassistant.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.personalassistant.navigation.Screen

/**
 * Phase 13：课表分组的长期设置
 *
 * 课表有两样长期设置：**学期**（有哪些学期、当前是哪个）与**课时表**（每天几节课、几点上课）。
 * 这两样都已经在 [com.example.personalassistant.feature.timetable.presentation.settings.TimetableSettingsScreen]
 * 里做得很完整（学期列表 + 课时表格编辑器），搬到设置页重写一遍只会做劣化。
 *
 * 所以这里做的是**入口**：告诉用户这组设置在哪、点什么能进去改。
 */
@Composable
fun TimetableSection(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModuleHeader(
            icon = Icons.Rounded.Today,
            title = "课表",
            subtitle = "学期与课时表",
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("课表设置", style = MaterialTheme.typography.titleMedium)
                Spacer8()
                Text(
                    text = "管理学期（新建 / 切换 / 删除）与课时表（每天几节课、每节几点开始）。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer8()
                OutlinedButton(
                    onClick = { navController.navigate(Screen.TimetableSettings.route) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开课表设置")
                    Spacer8()
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
