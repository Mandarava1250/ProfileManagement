package com.example.personalassistant.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.settings.BackupPreferencesUi
import com.example.personalassistant.feature.settings.theme.ThemeMode
import com.example.personalassistant.feature.settings.theme.ThemePreferences

/**
 * Phase 13：「我的」分组的长期设置
 *
 * 收纳四样跟「我自己」有关的设置：
 * - **主题**：外观（浅色 / 深色 / 跟随系统 + 动态配色）
 * - **GPA 规则**：学校定制换算表（入口跳已有独立页）
 * - **数据备份**：导出 / 恢复 / 自动备份
 * - **危险操作**：清除全部数据
 */
@Composable
fun ProfileSection(
    themePreferences: ThemePreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenGpaRule: () -> Unit,
    backupState: BackupPreferencesUi,
    onAutoBackupChange: (Boolean) -> Unit,
    onPickBackupFolder: () -> Unit,
    onBackupNow: () -> Unit,
    onPreviewRestore: () -> Unit,
    onQuickRestore: () -> Unit,
    resetRunning: Boolean,
    onRequestReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModuleHeader(
            icon = Icons.Filled.Person,
            title = "我的",
            subtitle = "外观、绩点、数据",
        )

        // ===== 主题 =====
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("主题", style = MaterialTheme.typography.titleMedium)
                Spacer8()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = themePreferences.themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                            )
                            Text(themeModeLabel(mode))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // 动态色开关（Android 12+ Material You）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("动态配色", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Android 12+ 使用系统强调色，关闭后回到 App 自带色板",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = themePreferences.dynamicColor,
                        onCheckedChange = onDynamicColorChange,
                    )
                }
            }
        }

        // ===== GPA 规则（跳已有独立页）=====
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, contentDescription = null)
                    Spacer8()
                    Text("GPA 规则", style = MaterialTheme.typography.titleMedium)
                }
                Spacer8()
                Text(
                    text = "自定义学校的绩点换算表：百分制区间、绩点上下限。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer8()
                OutlinedButton(
                    onClick = onOpenGpaRule,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("编辑绩点换算表")
                    Spacer8()
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }

        // ===== 数据备份 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Backup, contentDescription = null)
                    Spacer8()
                    Text(
                        "数据备份",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer8()
                Text(
                    "将课程/任务/事件/记账等数据导出为 .zip 文件，或从备份恢复。" +
                        "支持手动立即备份、自动每日备份到指定目录。",
                    style = MaterialTheme.typography.bodySmall,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // 自动备份开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动备份", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "每 24 小时自动备份到所选目录",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = backupState.autoBackupEnabled,
                        onCheckedChange = onAutoBackupChange,
                    )
                }

                Spacer8()

                // 备份目录选择
                OutlinedButton(
                    onClick = onPickBackupFolder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (backupState.hasFolderSelected) "更改备份目录" else "选择备份目录")
                }

                backupState.backupFolderUri.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "当前目录：$it",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer8()

                // 上次备份时间
                Text(
                    text = if (backupState.neverBackedUp) "尚未自动备份"
                    else "上次备份：${formatTimestamp(backupState.lastBackupAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // 立即备份
                Button(
                    onClick = onBackupNow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Backup, contentDescription = null)
                    Spacer8()
                    Text("立即备份")
                }

                Spacer8()

                // 恢复备份：两个入口 —— 「预览后导入」走新流程，
                // 「快速替换」走旧路径（整表替换、无预览）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 「预览后导入」：跳到 BackupPreviewScreen，由用户确认每张表的策略
                    OutlinedButton(
                        onClick = onPreviewRestore,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("预览后导入")
                    }
                    // 「快速替换」：保留旧逻辑（整表替换、不预览）。老用户/自动化一键恢复
                    OutlinedButton(
                        onClick = onQuickRestore,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Spacer8()
                        Text("快速替换")
                    }
                }
            }
        }

        // ===== 危险操作：清除全部数据 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer8()
                    Text(
                        "危险操作",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer8()
                Text(
                    text = "清除全部数据：14 张表 + 所有偏好 + 附件 + 头像，" +
                        "以及已调度的 Work 与提醒闹钟（闹钟需重启 App 才彻底清）。" +
                        "操作不可恢复，请先备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer8()
                OutlinedButton(
                    onClick = onRequestReset,
                    enabled = !resetRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer8()
                    Text("清除全部数据…")
                }
            }
        }
    }
}

/**
 * 主题模式 → 显示文案（Phase 8 Sub-step 6）
 *
 * 之所以放在 Composable 文件而不是 ThemeMode 枚举本身，
 * 是为了遵守"枚举本身与 Android string resource 解耦"的实践：
 * 主题 enum 是 domain 模型，文案归 UI 层。
 */
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    ThemeMode.SYSTEM -> "跟随系统"
}
