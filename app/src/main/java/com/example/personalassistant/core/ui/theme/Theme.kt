package com.example.personalassistant.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.personalassistant.feature.settings.theme.BackgroundStyle
import com.example.personalassistant.feature.settings.theme.ThemeMode

/**
 * App 主题入口
 *
 * @param themeMode 主题模式（[ThemeMode.SYSTEM] 默认，跟随系统）
 * @param dynamicColor Android 12+ 是否使用 Material You 动态色。
 *   Phase 14 起默认关闭 —— 壁纸取色会把品牌色整个盖掉，
 *   同一套 UI 在不同设备上颜色全不一样。设置页保留开关。
 * @param primaryColor 用户自选主色（ARGB）。null = 品牌默认蓝。
 *   开了 [dynamicColor] 时忽略这个值 —— 那时主色由壁纸决定。
 * @param backgroundStyle 背景风格（只在浅色主题下生效）。
 */
@Composable
fun PersonalAssistantTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    primaryColor: Int? = null,
    backgroundStyle: BackgroundStyle = BackgroundStyle.DEFAULT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 用户自选主色 → 在品牌色板上替换 primary 那一组
    // （动态色模式下不动：那时主色归壁纸管）
    val accentScheme = if (primaryColor != null && !dynamicColor) {
        applyAccent(baseScheme, Color(primaryColor), darkTheme)
    } else {
        baseScheme
    }

    // 背景风格：只在浅色下换底色。深色下再挑浅底会晃眼。
    val colorScheme = if (darkTheme) {
        accentScheme
    } else {
        val bg = Color(backgroundStyle.lightBackground)
        accentScheme.copy(background = bg, surface = bg)
    }

    // Phase 14：状态栏不再强制涂 primary。
    //
    // 原来的写法有两个问题：
    // 1. MainActivity 已经开了 enableEdgeToEdge()，这种模式下 statusBarColor 根本不生效；
    // 2. 就算生效，浅色主题下就是「深粉底 + 深色图标」，图标直接看不见。
    //
    // 现在只接管系统栏图标的明暗，跟着 App 自己的主题走 ——
    // App 可以跟系统的深浅不一致（用户选了浅色，系统是深色），所以不能交给系统决定。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // 语义色（收入 / 支出 / 预算 / 优先级）走 CompositionLocal 注入，深浅各一套
    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = ScrimLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = ScrimDark
)

/**
 * 用用户自选主色替换品牌色板里的 primary 一组
 *
 * 只动 primary / onPrimary / primaryContainer / onPrimaryContainer ——
 * 其余色（error / surface / outline…）保持品牌值。
 * 否则一个颜色就能把整套配色带跑偏（错误红变成绿的之类）。
 *
 * container 与 onContainer 从主色的 HSL 派生：色相不变、饱和度压低、明度分层，
 * 这样用户挑任何一个颜色，按钮底色和上面的字都能读得清。
 */
private fun applyAccent(base: ColorScheme, accent: Color, dark: Boolean): ColorScheme {
    val (hue, saturation, _) = argbToHsl(accent.toArgb())
    val container = Color.hsl(hue, saturation * 0.55f, if (dark) 0.30f else 0.90f)
    val onContainer = Color.hsl(hue, (saturation * 0.9f).coerceIn(0f, 1f), if (dark) 0.86f else 0.24f)
    return base.copy(
        primary = accent,
        onPrimary = onColorFor(accent),
        primaryContainer = container,
        onPrimaryContainer = onContainer,
    )
}

/**
 * ARGB → HSL（纯 Kotlin）
 *
 * 不用 android.graphics.Color.colorToHSL —— 那个在纯 JVM 单元测试里是空壳，
 * 而这段色板派生恰恰是最值得单测的部分（用户挑了很亮的黄，按钮底色还得压得住）。
 */
private fun argbToHsl(argb: Int): Triple<Float, Float, Float> {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, lightness)
    val delta = max - min
    val saturation = if (lightness > 0.5f) {
        delta / (2f - max - min)
    } else {
        delta / (max + min)
    }
    val hue = when (max) {
        r -> ((g - b) / delta + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / delta + 2f) / 6f
        else -> ((r - g) / delta + 4f) / 6f
    }
    return Triple(hue, saturation, lightness)
}
