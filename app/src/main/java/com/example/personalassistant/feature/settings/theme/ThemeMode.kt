package com.example.personalassistant.feature.settings.theme

/**
 * 主题模式（持久化在 DataStore 中的枚举）
 *
 * - [LIGHT] 始终浅色
 * - [DARK] 始终深色
 * - [SYSTEM] 跟随系统设置（默认）
 *
 * Phase 8 Sub-step 6 B 方案：从裸 String 升级为强类型枚举。
 * DataStore 里仍然存 String（key），但仓库层做序列化/反序列化。
 *
 * 向后兼容：
 * - 旧版本 DataStore 里存的 `theme_mode` 字符串（"light"/"dark"/"system"）直接复用
 * - 未知或 null 字符串 → [SYSTEM]（兜底，避免崩溃）
 */
enum class ThemeMode(val key: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        // 注意：const val 仅支持基本类型 + String，enum 类型不能 const
        val DEFAULT: ThemeMode = SYSTEM

        /**
         * 从持久化的 key 反序列化，容错：
         * - null / 空白 → [DEFAULT]
         * - 大小写不敏感（用户 DataStore 偶尔会写入大写）
         * - 未知值 → [DEFAULT]
         */
        fun fromKey(key: String?): ThemeMode {
            if (key.isNullOrBlank()) return DEFAULT
            val normalized = key.trim().lowercase()
            return entries.firstOrNull { it.key == normalized } ?: DEFAULT
        }
    }
}
