package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * Phase 7b：从 CSV 字符串批量导入流水 UseCase
 *
 * ## 行为
 * - 解析 CSV：跳过表头，按行字段解析（支持 RFC 4180 quoted fields）
 * - categoryId 优先匹配现存分类；找不到 → 抛 IllegalArgumentException
 * - date 支持 ISO 8601（UTC，"2026-09-06T12:34:56Z"）+ epoch millis（"1725627296000"）两种格式
 * - 缺字段 / 类型错误 / amount <= 0 → 加入 skipped 列表（不抛异常）
 *
 * ## 输出 [ImportSummary]
 * - imported: 成功条数
 * - skipped: 解析失败的行号 + 原因
 *
 * 入口由 FinanceViewModel.importTransactions() 调用，跑在 viewModelScope 协程里。
 */
class ImportTransactionsUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(csvText: String): ImportSummary {
        val rows = parseCsv(csvText)
        if (rows.isEmpty()) return ImportSummary(0, emptyList())

        // 第一行是表头，必须包含 date, type, amount, categoryId
        val header = rows.first().map { it.trim() }
        val idxDate = header.indexOf("date")
        val idxType = header.indexOf("type")
        val idxAmount = header.indexOf("amount")
        val idxCategoryId = header.indexOf("categoryId")
        val idxNote = header.indexOf("note")
        if (idxDate < 0 || idxType < 0 || idxAmount < 0 || idxCategoryId < 0) {
            throw IllegalArgumentException("CSV 表头缺少必要字段：date/type/amount/categoryId")
        }

        val categories = repository.observeCategories().first().associateBy { it.id }
        val dataRows = rows.drop(1)
        var imported = 0
        val skipped = mutableListOf<ImportSkip>()

        for ((i, cells) in dataRows.withIndex()) {
            val lineNo = i + 2 // header is line 1
            try {
                val isoDate = cells.getOrNull(idxDate)?.trim().orEmpty()
                val date = parseDate(isoDate)
                val type = when (cells.getOrNull(idxType)?.trim()) {
                    "0" -> TransactionType.EXPENSE
                    "1" -> TransactionType.INCOME
                    else -> throw IllegalArgumentException("未知 type")
                }
                val amount = cells.getOrNull(idxAmount)?.trim()?.toDoubleOrNull()
                    ?: throw IllegalArgumentException("amount 非数字")
                if (amount <= 0) throw IllegalArgumentException("amount <= 0")
                val catId = cells.getOrNull(idxCategoryId)?.trim()?.toLongOrNull()
                    ?: throw IllegalArgumentException("categoryId 非整数")
                if (!categories.containsKey(catId)) {
                    throw IllegalArgumentException("分类 id=$catId 不存在")
                }
                val note = idxNote.takeIf { it in cells.indices }
                    ?.let { cells[it].trim() }
                    ?.takeIf { it.isNotEmpty() }

                repository.addTransaction(
                    type = type,
                    amount = amount,
                    categoryId = catId,
                    note = note,
                    date = date,
                )
                imported++
            } catch (e: Exception) {
                skipped.add(ImportSkip(lineNo, e.message ?: "未知错误"))
            }
        }

        return ImportSummary(imported, skipped)
    }

    // ===== CSV 解析（支持 quoted fields） =====

    /**
     * RFC 4180 简化版：每行一个 record，每 record 一组 cells
     * 不支持跨行 quoted（export 不会产生跨行 field）
     */
    private fun parseCsv(text: String): List<List<String>> {
        return text.lineSequence()
            .filter { it.isNotBlank() }
            .map { parseCsvLine(it) }
            .toList()
    }

    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"')
                    i += 2
                    continue // 跳过外层 i++，避免多跳一格
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                }
                c == ',' && !inQuotes -> {
                    cells.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        cells.add(cur.toString())
        return cells
    }

    private fun parseDate(s: String): Long {
        // 优先 ISO 8601
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: s.toLongOrNull()
            ?: throw IllegalArgumentException("date 解析失败：$s")
    }

    data class ImportSummary(
        val imported: Int,
        val skipped: List<ImportSkip>,
    )

    data class ImportSkip(val lineNo: Int, val reason: String)
}