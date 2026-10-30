package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import com.example.personalassistant.feature.finance.presentation.util.IconRegistry
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * Phase 7b + Phase 2：交易行（一行展示一条流水）
 *
 * ## 7b
 * - 整行可点击（onClick 回调）→ 编辑流水入口
 * - 左侧：分类彩色圆形 + icon
 * - 中间：分类名 + 备注（或"无备注"）+ source
 * - 右侧：金额（支出负红色 / 收入正绿色） + 时间
 *
 * ## Phase 2 增量
 * - 中间列底部增加首张附件缩略图（32dp 圆形）— 仅当 `transaction.attachmentUri` 非空
 * - 缩略图是 file:// URI（Coil 直接加载）
 *
 * ## Phase 5 增量：跨账户转账
 * 转账行走**完全独立的一套显示**，而不是给收支样式打个补丁：
 * - 颜色用蓝灰而不是红/绿 —— 转账既不是花掉也不是挣到，给它上色就是说谎；
 * - 金额符号跟**方向**走（转出 − / 转入 +），跟收支无关；
 * - 标题固定「转出」/「转入」+ 对方账户名，不显示分类名。
 *
 * 为什么不显示「转账」分类名：分类被 [com.example.personalassistant.feature.finance.domain.repository.FinanceRepository.observeCategories]
 * 过滤掉了（它不该出现在分类管理页），所以这里传进来的 category 永远是 null。
 * 直接显示会退化成「未知分类」—— 那是人家自己引入的回归，必须在本行内部掉。
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
    // Phase 14：语义色统一从 LocalAppColors 取，组件层不再写死色值
    val appColors = LocalAppColors.current
    val categoryColor = category?.color?.let { Color(it) } ?: appColors.unknownCategory
    val iconName = category?.iconName ?: "Category"
    // Phase 2：首张附件（attachmentUri 是逗号分隔的 file:// URI 串）
    val firstAttachmentUri = transaction.attachmentUri
        ?.split(",")
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 分类彩色圆形
        val circleColor = if (isTransfer) appColors.transferInk else categoryColor
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = circleColor,
        ) {
            Icon(
                imageVector = if (isTransfer) {
                    Icons.Filled.SwapHoriz
                } else {
                    IconRegistry.resolve(iconName)
                },
                contentDescription = if (isTransfer) "转账" else categoryName,
                // Phase 14：分类色是用户自己挑的，可能很浅 —— 白图标会看不见。
                // 按底色亮度自动选黑/白，两种情况都保证可读。
                tint = if (isTransfer) {
                    appColors.onTransferContainer
                } else {
                    onColorFor(categoryColor)
                },
                modifier = Modifier.padding(8.dp),
            )
        }

        // 中间：分类名 + 备注 + source + [Phase 2] 附件缩略图
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (isTransfer) {
                    // 转账不显示分类名（那张分类已被observeCategories过滤掉，拿不到），
                    // 改用方向 + 对方账户名：这比「转账 · 无备注」信息量高得多
                    transferTitle(transaction, counterpartyName)
                } else {
                    categoryName
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            val noteOrEmpty = transaction.note?.takeIf { it.isNotBlank() } ?: "无备注"
            val sourceLabel = " · ${transaction.source}"
            Text(
                text = noteOrEmpty + sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            // Phase 2：首张附件缩略图（圆形 32dp）
            if (firstAttachmentUri != null) {
                AsyncImage(
                    model = firstAttachmentUri,
                    contentDescription = "附件",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(AppRadii.chip)),
                )
            }
        }

        // 右侧：金额 + 时间
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Phase 5：转账既不是花掉也不是挣到，用蓝灰；金额符号跟**方向**走。
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
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