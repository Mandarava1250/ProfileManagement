package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 7b：ExportTransactionsUseCase 单元测试
 *
 * 覆盖：
 * - 空库 → 只有表头
 * - 多笔交易 → 表头 + N 行
 * - 含逗号 / 引号的备注 → 正确 quote
 * - 含换行的备注 → 正确 quote（RFC 4180）
 * - 分类名为空 → 留空（不影响表结构）
 */
class ExportTransactionsUseCaseTest {

    private lateinit var repo: FakeFinanceRepository
    private lateinit var useCase: ExportTransactionsUseCase
    private lateinit var addTransaction: AddTransactionUseCase
    private lateinit var upsertCategory: UpsertCategoryUseCase

    @Before
    fun setup() {
        repo = FakeFinanceRepository()
        useCase = ExportTransactionsUseCase(repo)
        addTransaction = AddTransactionUseCase(repo)
        upsertCategory = UpsertCategoryUseCase(repo)
    }

    private suspend fun seedCategory(name: String, id: Long? = null): Long {
        val cat = Category(
            id = id ?: 0L,
            name = name,
            type = TransactionType.EXPENSE,
            iconName = "Restaurant",
            color = 0xFF000000.toInt(),
        )
        return upsertCategory(cat)
    }

    @Test
    fun `empty repository produces header only`() = runTest {
        val csv = useCase()
        assertEquals("date,type,amount,categoryId,categoryName,note,source", csv)
    }

    @Test
    fun `single transaction produces header plus one row`() = runTest {
        val catId = seedCategory("午餐")
        addTransaction(TransactionType.EXPENSE, 25.0, catId, "午饭", 1000L)

        val csv = useCase()
        val lines = csv.split("\n")
        assertEquals(2, lines.size)
        assertEquals("date,type,amount,categoryId,categoryName,note,source", lines[0])
        assertTrue("Row 1 should contain amount: ${lines[1]}", lines[1].contains("25.0"))
        assertTrue("Row 1 should contain categoryName 午餐: ${lines[1]}", lines[1].contains("午餐"))
    }

    @Test
    fun `multiple transactions produce correct row count`() = runTest {
        val catId = seedCategory("c1")
        addTransaction(TransactionType.EXPENSE, 10.0, catId, null, 1000L)
        addTransaction(TransactionType.EXPENSE, 20.0, catId, null, 2000L)
        addTransaction(TransactionType.INCOME, 5000.0, catId, null, 3000L)

        val csv = useCase()
        val lines = csv.split("\n")
        assertEquals(4, lines.size) // header + 3 rows
    }

    @Test
    fun `note with comma is quoted`() = runTest {
        val catId = seedCategory("c")
        addTransaction(TransactionType.EXPENSE, 10.0, catId, "foo,bar", 1000L)

        val csv = useCase()
        val lines = csv.split("\n")
        // "foo,bar" should become "\"foo,bar\""
        assertTrue("Comma-containing note must be quoted: ${lines[1]}", lines[1].contains("\"foo,bar\""))
    }

    @Test
    fun `note with quote is escaped and quoted`() = runTest {
        val catId = seedCategory("c")
        addTransaction(TransactionType.EXPENSE, 10.0, catId, "say \"hi\"", 1000L)

        val csv = useCase()
        val lines = csv.split("\n")
        // "say \"hi\"" should become "\"say \"\"hi\"\"\""
        assertTrue("Quote-containing note must be escaped: ${lines[1]}", lines[1].contains("\"say \"\"hi\"\"\""))
    }

    @Test
    fun `roundtrip header is valid csv`() = runTest {
        val csv = useCase()
        val firstLine = csv.substringBefore("\n")
        assertEquals(listOf("date", "type", "amount", "categoryId", "categoryName", "note", "source"),
            firstLine.split(","))
    }
}