package com.example.personalassistant.core.utils

import java.text.DecimalFormat
import java.util.Locale

/**
 * 货币格式化工具
 */
object CurrencyFormatter {

    private val FORMATTER = DecimalFormat("#,##0.00")
    private val FORMATTER_NO_DECIMAL = DecimalFormat("#,##0")

    /** 默认货币符号 */
    const val DEFAULT_SYMBOL = "¥"

    /**
     * 格式化金额：保留两位小数，加千分位
     * 例：1234.5 -> "¥1,234.50"
     */
    fun format(
        amount: Double,
        symbol: String = DEFAULT_SYMBOL,
        showSymbol: Boolean = true
    ): String {
        val prefix = if (showSymbol) symbol else ""
        return "$prefix${FORMATTER.format(amount)}"
    }

    /** 格式化金额（不含小数）："¥1,234" */
    fun formatCompact(amount: Double, symbol: String = DEFAULT_SYMBOL): String =
        "$symbol${FORMATTER_NO_DECIMAL.format(amount)}"

    /**
     * 解析用户输入的金额字符串为 Double
     * 支持：
     * - "¥12.34" / "￥12.34" / "$12.34"
     * - "1,234.56"
     * - "12.34" / "12"
     * 返回 null 表示格式错误
     */
    fun parse(text: String): Double? {
        if (text.isBlank()) return null
        val cleaned = text.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace("$", "")
            .replace(",", "")
        return cleaned.toDoubleOrNull()?.takeIf { it >= 0 }
    }

    /** 带符号的快捷格式化 */
    fun formatYuan(amount: Double): String = format(amount, "¥")

    /** 带正负号的格式化（用于显示盈亏） */
    fun formatSigned(amount: Double, symbol: String = DEFAULT_SYMBOL): String {
        val sign = if (amount >= 0) "+" else "-"
        return "$sign$symbol${FORMATTER.format(kotlin.math.abs(amount))}"
    }
}
