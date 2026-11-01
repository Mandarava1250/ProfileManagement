package com.example.personalassistant.feature.task.presentation.util

/**
 * Phase 6c：标签调色板（UI 工具层）
 *
 * ## 设计动机
 * - 标签颜色从「固定 6 色板」起步，UI 层通过 [TagPaletteSource] 拿色板
 * - 未来 v扩展：
 *   - 加用户自定义色板：新建 `UserTagPaletteSource(prefs)` 实现 [TagPaletteSource]
 *   - 加主题色集成：实现 ThemeAwareTagPaletteSource 从 MaterialTheme 派生
 *   - 加远程色板：实现 RemoteTagPaletteSource 从云端拉
 *
 * ## 与 data 层的边界
 * - 本文件**不**依赖 Room / Domain / Repository，纯 UI 工具
 * - TagEntity.colorHex 落的是 ARGB Long（0xFFFF69B4），由 Repository 层负责写入
 * - UI 选色时从 [TagPaletteSource] 拿 [TagPalette]，再读取 .hex 写库
 *
 * ## 6 色板
 * | id       | displayName | hex       |
 * |----------|-------------|-----------|
 * | pink     | Pink        | #FF69B4   |
 * | purple   | Purple      | #B388FF   |
 * | blue     | Blue        | #64B5F6   |
 * | green    | Green       | #81C784   |
 * | orange   | Orange      | #FFB74D   |
 * | grey     | Grey        | #9E9E9E   |
 */

/**
 * 色板中的单个颜色条目。
 *
 * @param id 颜色在调色板中的唯一 id（如 "pink"、"purple"），未来可作远程色板引用
 * @param displayName 人类可读名（UI 显示用）
 * @param hex ARGB 颜色值（不透明色），如 0xFFFF69B4
 */
data class TagPalette(
    val id: String,
    val displayName: String,
    val hex: Long,
)

/**
 * 6 色固定调色板（plan v3.3 决策）。
 *
 * 暴露为 `entries` 列表，方便 [DefaultTagPaletteSource.all] 直接转发。
 */
object DefaultTagPalette {
    val entries: List<TagPalette> = listOf(
        TagPalette(id = "pink", displayName = "Pink", hex = 0xFFFF69B4),
        TagPalette(id = "purple", displayName = "Purple", hex = 0xFFB388FF),
        TagPalette(id = "blue", displayName = "Blue", hex = 0xFF64B5F6),
        TagPalette(id = "green", displayName = "Green", hex = 0xFF81C784),
        TagPalette(id = "orange", displayName = "Orange", hex = 0xFFFFB74D),
        TagPalette(id = "grey", displayName = "Grey", hex = 0xFF9E9E9E),
    )

    /** 默认色（新建标签时自动选中） */
    val defaultEntry: TagPalette = entries.first() // Pink
}

/**
 * 标签调色板数据源接口（repository 模式在 UI 层的应用）。
 *
 * 由 [DefaultTagPaletteSource] 提供默认实现；未来替换实现即可升级色板
 * （无需修改任何 Composable / ViewModel 代码）。
 */
interface TagPaletteSource {
    /** 返回所有可选颜色（按 [DefaultTagPalette.entries] 的顺序） */
    fun all(): List<TagPalette>
}

/**
 * 默认实现：返回 [DefaultTagPalette.entries]。
 *
 * 由 Hilt 直接 new 一个实例（无 IO，无状态），用 @Provides 注入即可。
 */
class DefaultTagPaletteSource : TagPaletteSource {
    override fun all(): List<TagPalette> = DefaultTagPalette.entries
}