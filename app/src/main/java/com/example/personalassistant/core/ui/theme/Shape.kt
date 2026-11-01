package com.example.personalassistant.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角刻度 —— Phase 14 樱花粉 2.0
 *
 * 扩档到 8 / 12 / 16 / 20 / 28 五档，与 [AppRadii] 一一对应：
 * extraSmall=8（chip）、small=12（输入 / chip）、medium=16（输入 / 卡片）、
 * large=20（卡片 / FAB）、extraLarge=28（大卡 / 弹层）。
 *
 * 取值与 [AppRadii] 保持一致：Material 3 组件走这里的 shapes，
 * 自绘组件走 AppRadii，两边不会出现「同一个东西两种圆角」。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
