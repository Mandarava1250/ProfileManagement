package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.personalassistant.core.ui.components.ColorBar
import com.example.personalassistant.core.ui.components.ensureBarContrast
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * 交易行（Phase 7b → Phase 26 重画）
 *
 * ## Phase 26 的两处结构变化
 *
 * 1. **40dp 彩色圆形 + 图标 → 3dp 分类色竖条**
 *    圆形图标占掉一整列宽度，但它在列表里的信息量只有「这是哪个分类」——
 *    而分类名就在标题上。改成竖条后行更窄、扫视更快，颜色仍然在做标识。
 *    竖条是「非文字的 UI 元素」，浅色分类在白底上会糊掉，所以走
 *    [ensureBarContrast] 压暗到 ≥ 3:1。
 *
 * 2. **副行去掉了 `· SOURCE` 这个机器字段**
 *    原来副行是「备注 · 手动」/「无备注 · CSV导入」——「手动 / CSV导入」
 *    是数据来源，用户在流水列表里不关心，它把真正有用的备注挤没了。
 *    现在副行只显示备注；时间本来就在右侧，不会再重复一遍。
 *
 * ## 转账行是完全独立的一套显示
 *
 * - 颜色用蓝灰而不是红/绿 —— 转账既不是花掉也不是挣到，给它上色就是说谎；
 * - 金额符号跟**方向**走（转出 − / 转入 +），跟收支无关；
 * - 标题固定「转出」/「转入」+ 对方账户名，不显示分类名。
 *
 * 为什么不显示「转账」分类名：分类被 `observeCategories` 过滤掉了
 * （它不该出现在分类管理页），所以这里传进来的 category 永远是 null。
 * 直接显示会退化成「未知分类」—— 那是自己引入的回归，必须在本行内部掉。
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** 转账对方账户名（Phase 5）；null 时降级为不显示“自/至”部分 */
    counterpartyName: String? = null,
) {
    val isTransfer = transaction.type.isTransfer
    val categoryName = category?.name ?: "未知分类"
    // 语义色统一从 LocalAppColors 取，组件层不再写死色值
    val appColors = LocalAppColors.current
    val categoryColor = category?.color?.let { Color(it) } ?: appColors.unknownCategory
    // 首张附件（attachmentUri 是逗号分隔的 file:// URI 串）
    val firstAttachmentUri = transaction.attachmentUri
        ?.split(",")
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            // 左右 20dp 跟大标题 / 分组标题对齐（原来是 16dp，比标题少 4dp，
            // 整页左缘看起来是锯齿状的）
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        // 3dp 分类色竖条（转账用蓝灰，既不红也不绿）
        val barColor = if (isTransfer) appColors.transferInk else ensureBarContrast(categoryColor)
        ColorBar(
            color = barColor,
            modifier = Modifier.height(28.dp),
        )

        // 中间：标题 + 备注 + 附件缩略图
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (isTransfer) {
                    // 转账不显示分类名（那张分类已被过滤，拿不到），
                    // 改用方向 + 对方账户名：比「转账 · 无备注」信息量高得多
                    transferTitle(transaction, counterpartyName)
                } else {
                    categoryName
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            // 副行只留备注。空备注时写「无备注」而不是整行消失 ——
            // 行高一跳一跳的列表很难扫。
            Text(
                text = transaction.note?.takeIf { it.isNotBlank() } ?: "无备注",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            // 首张附件缩略图（24dp 圆角方块，比原来的 32dp 圆形更贴行高）
            if (firstAttachmentUri != null) {
                AsyncImage(
                    model = firstAttachmentUri,
                    contentDescription = "附件",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = AppSpacing.xxs)
                        .size(24.dp)
                        .clip(RoundedCornerShape(AppRadii.chip)),
                )
            }
        }

        // 右侧：金额 + 时间
        Box {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 转账既不是花掉也不是挣到，用蓝灰；金额符号跟**方向**走。
                // 不写成 `else -> 红`：这两个 when 是 enum when，本来就有穷尽保护
                // （新增 TRANSFER_* 时编译器就是在这里直接报的错）；
                // 加了 else 等于把这份保护关掉，下次再加类型就没人提醒了。
                val signed = when (transaction.type) {
                    TransactionType.EXPENSE -> -transaction.amount
                    TransactionType.INCOME -> transaction.amount
                    TransactionType.TRANSFER_OUT -> -transaction.amount
                    TransactionType.TRANSFER_IN -> transaction.amount
                }
                val color = when (transaction.type) {
                    TransactionType.EXPENSE -> appColors.expenseInk
                    TransactionType.INCOME -> appColors.incomeInk
                    TransactionType.TRANSFER_OUT -> appColors.transferInk
                    TransactionType.TRANSFER_IN -> appColors.transferInk
                }
                Text(
                    text = MoneyFormat.format(signed),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
                Text(
                    text = FinanceDateUtils.formatTimeOfDay(transaction.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 转账行标题：「转出 至 微信」/「转入 自 支付宝」
 *
 * 对方名缺失时（对应账户已被删、或旧备份导入的半对数据）只显「转出」/「转入」，
 * 不显示「未知账户」—— 那个字会让用户以为了不起的事发生了，
 * 而真实情况只是「对方账户已经不在了」。
 */
private fun transferTitle(transaction: Transaction, counterpartyName: String?): String {
    val direction = if (transaction.type == TransactionType.TRANSFER_OUT) "转出" else "转入"
    val name = counterpartyName?.takeIf { it.isNotBlank() } ?: return direction
    val preposition = if (transaction.type == TransactionType.TRANSFER_OUT) "至" else "自"
    return "$direction $preposition $name"
}