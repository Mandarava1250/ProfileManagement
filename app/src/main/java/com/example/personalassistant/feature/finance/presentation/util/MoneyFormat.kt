package com.example.personalassistant.feature.finance.presentation.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Phase 7a：金额格式化
 *
 * - 默认中文 locale：保留 2 位小数 + 千分位逗号（"¥1,234.56"）
 * - 负数红色显示（Composed 调用方处理颜色）
 */
object MoneyFormat {

    private val format: NumberFormat = NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /**
     * 格式化金额，带 ¥ 符号。
     * - 正数 → "¥1,234.56"
     * - 负数 → "¥-1,234.56"
     * - 0 → "¥0.00"
     */
    fun format(amount: Double): String {
        return "¥" + format.format(amount)
    }

    /** 仅数字，不带符号（用于编辑框） */
    fun formatPlain(amount: Double): String = format.format(amount)

    /**
     * 格式化百分比（0..1 → "12%"）
     * - ratio=0.1234 → "12%"
     * - ratio=1.5 → "150%"（超支）
     */
    fun formatPercent(ratio: Double): String {
        return "${(ratio * 100).toInt()}%"
    }
}
