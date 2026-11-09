package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.core.ui.theme.LocalAppSurfaces
import com.example.personalassistant.feature.finance.domain.model.AccountBudgetSummary
import com.example.personalassistant.feature.finance.domain.model.BudgetSummaryTotals
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * Phase 5：跨账户预算总览卡
 *
 * ## 显示三块信息，顺序有讲究
 * 1. **合计**（预算 / 支出 / 剩余 + 进度条）—— 用户最关心的「这个月还能花多少」
 * 2. **缺口提示**（N 个账户未设预算）—— 合计的可信度说明
 * 3. **逐账户行**（预算、支出、超支标记）
 *
 * 第 2 条不能省。合计是「有预算的账户」之和，若某账户没设预算，它就不在分母里；
 * 只报一个总数，用户会以为那是全部账户的预算上限，实际少算了一个账户 ——
 * 与 Phase 3 那个「静默串账」是同一类病灶：数字看着对，结论是错的。
 *
 * ## 转账不计入支出
 * 每行的 transferIn/transferOut 单独展示，不并进 expense。账户之间挪钱不是消费，
 * 混进去月度报表就会凭空多出一笔不存在的支出（这也是 TransactionType 新增枚举值
 * 而统计 SQL 一条不改的原因）。
 */
@Composable
fun AccountSummaryCard(
    totals: BudgetSummaryTotals,
    rows: List<AccountBudgetSummary>,
    modifier: Modifier = Modifier,
) {
    // 单账户时这张卡没有信息增益（主页已有该账户的预算/零钱池卡），不显示。
    // 但仍要能在 rows.size>=2 时出现 —— 所以判断放这里而不是让调用方传 flag，
    // 免得两处条件写歪导致「只有一个账户时合计看起来像全账户」。
    if (rows.size < 2) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppSurfaces.current.paper),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "全部账户合计（本月）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryStat(label = "预算", value = MoneyFormat.formatPlain(totals.totalBudget))
                SummaryStat(label = "支出", value = MoneyFormat.formatPlain(totals.totalExpense))
                SummaryStat(
                    label = "剩余",
                    value = MoneyFormat.formatPlain(totals.totalRemaining),
                    negative = totals.totalRemaining < 0.0,
                )
            }

            // 进度条：usedRatio 已在模型里做过「预算为 0 时返回 0」的保护
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(3.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(totals.usedRatio.coerceIn(0.0, 1.0).toFloat())
                        .height(6.dp)
                        .background(
                            if (totals.usedRatio > 1.0) {
                                LocalAppColors.current.expenseInk
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            RoundedCornerShape(3.dp),
                        ),
                )
            }

            if (totals.accountsMissingBudget > 0) {
                Text(
                    text = "另有 ${totals.accountsMissingBudget} 个账户未设预算，" +
                        "未计入上面的合计与进度",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.expenseInk,
                )
            }

            // 转账两边对不上 = 数据异常（半对转账 / 导入只进一半），必须说而不是取平均
            if (totals.transferImbalanced) {
                Text(
                    text = "转账数据不平衡：转入 ${MoneyFormat.formatPlain(totals.totalTransferIn)}，" +
                        "转出 ${MoneyFormat.formatPlain(totals.totalTransferOut)}，请检查流水",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.expenseInk,
                )
            }

            if (totals.totalTransfer > 0.0 && !totals.transferImbalanced) {
                Text(
                    text = "本月转账 ${MoneyFormat.formatPlain(totals.totalTransfer)}（账户间调拨，不计入支出）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            rows.forEach { row ->
                AccountBudgetRow(row = row)
            }
        }
    }
}

@Composable
private fun AccountBudgetRow(row: AccountBudgetSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.accountName,
                style = MaterialTheme.typography.bodyMedium,
            )
            // 转账净额单独一行：它改变余额但不改变支出，混进下一行会误导
            if (row.transferIn > 0.0 || row.transferOut > 0.0) {
                Text(
                    text = "转入 ${MoneyFormat.formatPlain(row.transferIn)}" +
                        " / 转出 ${MoneyFormat.formatPlain(row.transferOut)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (row.budgetMissing) {
                "未设预算 · 支出 ${MoneyFormat.formatPlain(row.expense)}"
            } else {
                "${MoneyFormat.formatPlain(row.expense)} / ${MoneyFormat.formatPlain(row.budgetAmount ?: 0.0)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (row.isOverBudget) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                row.isOverBudget -> LocalAppColors.current.expenseInk
                row.budgetMissing -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun SummaryStat(label: String, value: String, negative: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (negative) LocalAppColors.current.expenseInk else MaterialTheme.colorScheme.onSurface,
        )
    }
}
