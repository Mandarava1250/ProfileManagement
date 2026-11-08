package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalassistant.core.ui.components.ThinProgressBar
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.core.ui.theme.LocalAppSurfaces
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingResult
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * 「今日剩余」主卡 —— Phase 26 的 **A 结构 × C 皮肤**。
 *
 * ## 结构来自方案 A
 *
 * 一块大数字 + 一句口径说明 + 一根进度条 + 一行脚注。用户扫一眼就知道
 * 「今天还能花多少」，不需要读三个数字再做减法。
 *
 * ## 皮肤来自方案 C
 *
 * 暖白底（[LocalAppSurfaces.paper]）+ 左侧 3dp 品牌蓝竖条，而不是原来的
 * `primaryContainer` 整块铺色。铺色卡在暖白页面里会跳出来抢注意力，
 * 而这块卡本来就在第一屏第一眼的位置，不需要再靠颜色争。
 *
 * 竖条没有用 `AppCard(accent = ...)`：`AppCard` 内部固定了 20dp 圆角，
 * 而这里要的是 14dp，且竖条要贴着卡片左缘 —— 自己画一个 `Box` 更直接。
 * 竖条用 `matchParentSize` 叠在内容层上，不参与尺寸计算，也就不需要
 * `IntrinsicSize.Min` 那套会崩在 LazyList 里的测量。
 *
 * ## 大字号为什么是「纯计算」
 *
 * 它来自 [TodayRemainingResult.amount] —— 由「本月可用余额 − 本月已支出」现算，
 * 改预算立刻生效。**不读** `daily_pools.remainingAmount`：那是当天发下来的
 * 日额度快照，改预算后纹丝不动（这正是批次 C 修掉的 bug）。
 *
 * | | FIXED（固定） | AUTO（自动） |
 * |---|---|---|
 * | 大字号 | 本月剩余额度 | 今日可用 = 本月剩余 ÷ 剩余天数 |
 * | 副行左 | 本月预算 ¥3,000.00 | 本月剩余 ¥2,000.00 · 按剩余 10 天分摊 |
 * | 副行右 | 本月已用 33% | 本月已用 33% |
 *
 * @param result 今日剩余的派生结果（金额 / 本月剩余 / 剩余天数 / 口径）
 * @param warningThreshold 警告阈值（剩余金额低于此值时显示临界色）。0 表示禁用。
 * @param onToggleMode 点击右上角口径标签时的回调；null = 不可切换（只读展示）
 */
@Composable
fun DailyPoolCard(
    result: TodayRemainingResult,
    monthBudget: Double?,
    monthExpense: Double?,
    warningThreshold: Double = 0.0,
    onToggleMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val surfaces = LocalAppSurfaces.current
    val brand = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(AppRadii.card14)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaces.paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 左侧多留 3dp：竖条压在这段内边距里，不挤内容
                .padding(
                    start = AppSpacing.lg + 3.dp,
                    end = AppSpacing.lg,
                    top = AppSpacing.md,
                    bottom = AppSpacing.md,
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            // ===== 标题行：左「今日剩余」+ 右「口径标签」=====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "今日剩余",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 口径切换做成一个小胶囊。
                //
                // 这里是「可点的状态标签」而不是一个 Button：它同时承担
                // 「现在用的是哪种口径」的信息职能，做成按钮会让人以为
                // 点下去会执行一次计算。
                Text(
                    text = "${result.mode.displayName} ⌄",
                    style = MaterialTheme.typography.labelMedium,
                    color = brand,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadii.chip))
                        .background(brand.copy(alpha = 0.10f))
                        .then(
                            if (onToggleMode != null) {
                                Modifier.clickable { onToggleMode() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = AppSpacing.xs, vertical = 3.dp),
                )
            }

            // ===== 大字号：按口径二选一（结果为 null = 未设预算）=====
            val remaining = result.amount
            val amountColor = when {
                remaining == null -> MaterialTheme.colorScheme.onSurfaceVariant
                remaining < 0 -> appColors.expenseInk                      // 红：超支
                warningThreshold > 0.0 && remaining < warningThreshold ->
                    appColors.budgetInk                                    // 橙：临界
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = if (remaining == null) "未设置预算" else MoneyFormat.format(remaining),
                // 34sp Light：大但不压迫。粗体大数字在暖白底上太重，
                // 会让整块卡看起来像警告横幅。
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Light,
                ),
                color = amountColor,
            )

            // ===== 副行：模式相关的补充说明 + 本月已用 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = subtitleLeftText(result),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = monthlyUsageText(monthBudget, monthExpense),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ===== 进度条：本月已用 / 月预算 =====
            val ratio = monthlyUsageRatio(monthBudget, monthExpense)
            if (ratio != null) {
                ThinProgressBar(
                    progress = ratio.coerceIn(0.0, 1.0).toFloat(),
                    height = 3.dp,
                    trackColor = surfaces.barTrack,
                    progressColor = if (ratio > 1.0) appColors.expenseInk else brand,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 点明本卡与「零钱池」卡的关系。
            //
            // 「今日剩余」是今天还能花多少（整天在变）；
            // 「零钱池」是历次日终结转累加下来的钱（每天只在日终变一次）。
            // 两张卡都是「¥」，不点明的话用户会以为零钱池 = 今日剩余。
            Text(
                text = "日终自动存入零钱池",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        // 左侧 3dp 品牌蓝竖条。
        //
        // 叠在内容层上、绝对定位：matchParentSize 不参与尺寸计算，
        // 只跟随父级已定好的尺寸，所以竖条既不会把卡撑高，也不会挤走内容。
        // 父级已经 clip 了 14dp 圆角，竖条会自然跟着左缘的圆角走。
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(brand),
            )
        }
    }
}

/**
 * 副行的左半句（按口径）
 *
 * - FIXED：大数字已经是「本月剩余」→ 这里给「本月预算」（总额），不重复
 * - AUTO ：大数字是「今日可用」→ 这里给「本月剩余」+ 分摊天数
 */
private fun subtitleLeftText(result: TodayRemainingResult): String = when (result.mode) {
    TodayRemainingMode.FIXED -> {
        val total = result.monthRemaining
        if (total == null) "本月 —" else "本月预算 ${MoneyFormat.format(total)}"
    }
    TodayRemainingMode.AUTO -> {
        val remaining = result.monthRemaining
        if (remaining == null) {
            "本月 —"
        } else {
            "本月剩余 ${MoneyFormat.format(remaining)} · 按剩余 ${result.remainingDays} 天分摊"
        }
    }
}

private fun monthlyUsageText(monthBudget: Double?, monthExpense: Double?): String {
    if (monthBudget == null || monthBudget <= 0.0) return "本月 —"
    val expense = monthExpense ?: 0.0
    val percent = MoneyFormat.formatPercent(expense / monthBudget)
    return "本月已用 $percent"
}

private fun monthlyUsageRatio(monthBudget: Double?, monthExpense: Double?): Double? {
    if (monthBudget == null || monthBudget <= 0.0) return null
    val expense = monthExpense ?: 0.0
    return expense / monthBudget
}