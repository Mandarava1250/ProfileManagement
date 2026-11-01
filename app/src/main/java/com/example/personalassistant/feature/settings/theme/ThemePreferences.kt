package com.example.personalassistant.feature.settings.theme

/**
 * 主题偏好快照（Phase 8 Sub-step 6 → Phase 14 扩展）
 *
 * 把 [themeMode]（裸 String）和 [dynamicColor]（未持久化参数）
 * 合并成一个不可变状态对象，便于：
 * 1. UI 一次 collect 拿到全部主题相关字段（避免多 Flow 同步竞态）
 * 2. 主题切换时 dynamicColor 一起回填默认值
 * 3. 后续扩展示色板/字体缩放时仅需在 data class 加字段
 *
 * 默认值：
 * - mode = SYSTEM：尊重用户系统设置
 * - dynamicColor = false：Phase 14 起默认走品牌色板（默认蓝），
 *   想用 Material You 壁纸取色的用户在设置页自行打开
 * - primaryColor = null：走品牌默认蓝，用户在「我 → 外观」里选了才写值
 * - backgroundStyle = DEFAULT：默认背景
 */
data class ThemePreferences(
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val dynamicColor: Boolean = false,
    /** 用户自选主色（ARGB int），null = 品牌默认蓝 */
    val primaryColor: Int? = null,
    /** 背景风格 */
    val backgroundStyle: BackgroundStyle = BackgroundStyle.DEFAULT,
)

/**
 * 背景风格（Phase 14）
 *
 * 用户能挑的「底色」预设。只影响浅色主题下的 background / surface；
 * 深色主题下统一用深色（深色下再挑浅底会晃眼）。
 *
 * @param key DataStore 里存的字符串
 * @param label UI 上显示的名字
 * @param lightBackground 浅色主题下的背景色
 */
enum class BackgroundStyle(
    val key: String,
    val label: String,
    val lightBackground: Long,
) {
    /** 默认：冷调白，跟 Material 3 蓝色主色最搭 */
    DEFAULT("default", "默认", 0xFFFAFBFF),

    /** 暖白：偏米，像纸 */
    PAPER("paper", "暖白", 0xFFFBF8F4),

    /** 薄荷：淡青绿 */
    MINT("mint", "薄荷", 0xFFF4FAF7),

    /** 天蓝：淡蓝 */
    SKY("sky", "天蓝", 0xFFF5F8FD),

    /** 浅灰：中性 */
    FOG("fog", "浅灰", 0xFFF7F7F9),
    ;

    companion object {
        fun fromKey(key: String?): BackgroundStyle =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
