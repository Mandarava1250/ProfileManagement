package com.example.personalassistant.feature.settings.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Phase 8 Sub-step 6：ThemeMode 单元测试
 *
 * 重点：
 * - enum 三个值
 * - fromKey 容错：null / 空 / 大小写 / 未知值
 * - 默认值常量
 */
class ThemeModeTest {

    @Test
    fun `LIGHT and DARK and SYSTEM are the only enum values`() {
        // 三档枚举，未来扩展示色板/字号时再加
        assertEquals(3, ThemeMode.entries.size)
        assertEquals(
            setOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
            ThemeMode.entries.toSet(),
        )
    }

    @Test
    fun `key 字符串与旧 DataStore 兼容`() {
        // 旧版本 SettingsRepository 写过 "light"/"dark"/"system"
        // 新版本 ThemeRepositoryImpl 也读同一个 key，所以 key 必须严格匹配
        assertEquals("light", ThemeMode.LIGHT.key)
        assertEquals("dark", ThemeMode.DARK.key)
        assertEquals("system", ThemeMode.SYSTEM.key)
    }

    @Test
    fun `fromKey 接受小写字符串`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("system"))
    }

    @Test
    fun `fromKey 容错 null blank 未知值 大小写`() {
        // null / 空字符串 / 纯空白 → DEFAULT
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromKey(""))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromKey("   "))
        // 未知值 → DEFAULT（避免升级期间崩溃）
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromKey("midnight"))
        // 大小写不敏感
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("Light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("DARK"))
        // 前后空格容忍
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(" system "))
    }

    @Test
    fun `DEFAULT 应为 SYSTEM（保持跟随系统这一最保守默认值）`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT)
        // 反向断言：避免未来误改
        assertNotEquals(ThemeMode.LIGHT, ThemeMode.DEFAULT)
    }
}
