package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Phase 7b：导出全部流水为 CSV 字符串 UseCase
 *
 * CSV 格式：
 * ```
 * date,type,amount,categoryId,categoryName,note,source
 * 2026-09-06T12:34:56Z,0,25.5,1,午餐,午饭,手动
 * ...
 * ```
 *
 * - date 用 ISO 8601（UTC）便于跨时区导入
 * - type：0=支出, 1=收入
 * - 字段含逗号 / 引号 / 换行时按 RFC 4180 quote（双引号包裹 + 内部 " 转义为 ""）
 * - 表头固定
 *
 * 调用方（FinanceViewModel）再把 String 写到 SAF / Intent.EXTRA_STREAM。
 */
class ExportTransactionsUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(): String {
        val txList = repository.observeAllTransactions().first()
        val categoryMap = repository.observeCategories().first().associateBy { it.id }
        return toCsv(txList, categoryMap)
    }

    private fun toCsv(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
    ): String {
        val header = "date,type,amount,categoryId,categoryName,note,source"
        val rows = transactions.map { tx ->
            val catName = categories[tx.categoryId]?.name ?: ""
            val isoDate = ISO_FORMATTER.format(
                Instant.ofEpochMilli(tx.date).atZone(ZoneId.systemDefault()).toInstant()
            )
            listOf(
                isoDate,
                tx.type.storageValue.toString(),
                tx.amount.toString(),
                tx.categoryId.toString(),
                catName,
                tx.note ?: "",
                tx.source,
            ).joinToString(",") { escapeCsv(it) }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun escapeCsv(s: String): String {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }

    companion object {
        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
    }
}