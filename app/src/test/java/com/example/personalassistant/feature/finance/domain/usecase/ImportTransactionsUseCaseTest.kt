package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 7b：ImportTransactionsUseCase 单元测试
 *
 * 覆盖：
 * - 合法 CSV → 全部入库
 * - 缺字段行 → skipped（其它行不受影响）
 * - 非法 amount / type / date → skipped
 * - 不存在的 categoryId → skipped
 * - 表头缺必要字段 → 抛 IllegalArgumentException
 */
class ImportTransactionsUseCaseTest {

    private lateinit var repo: FakeFinanceRepository
    private lateinit var useCase: ImportTransactionsUseCase
    private lateinit var addTransaction: AddTransactionUseCase
    private lateinit var upsertCategory: UpsertCategoryUseCase

    @Before
    fun setup() {
        repo = FakeFinanceRepository()
        useCase = ImportTransactionsUseCase(repo)
        addTransaction = AddTransactionUseCase(repo)
        upsertCategory = UpsertCategoryUseCase(repo)
    }

    private suspend fun seedCategory(): Long {
        return upsertCategory(
            Category(
                name = "测试分类",
                type = TransactionType.EXPENSE,
                iconName = "Restaurant",
                color = 0xFF000000.toInt(),
            )
        )
    }

    @Test
    fun `valid csv imports all rows`() = runTest {
        val catId = seedCategory()
        val csv = """
            date,type,amount,categoryId,categoryName,note,source
            2026-09-06T12:00:00Z,0,25.5,$catId,测试分类,午饭,手动
            2026-09-06T13:00:00Z,1,5000.0,$catId,测试分类,工资,手动
        """.trimIndent()

        val summary = useCase(csv)
        assertEquals(2, summary.imported)
        assertEquals(0, summary.skipped.size)

        val list = repo.observeAllTransactions().first()
        assertEquals(2, list.size)
    }

    @Test
    fun `quoted note is correctly unescaped`() = runTest {
        val catId = seedCategory()
        val csv = """
            date,type,amount,categoryId,categoryName,note,source
            2026-09-06T12:00:00Z,0,10.0,$catId,测试分类,"包含,逗号",手动
        """.trimIndent()

        val summary = useCase(csv)
        assertEquals(1, summary.imported)
        val list = repo.observeAllTransactions().first()
        assertEquals("包含,逗号", list[0].note)
    }

    @Test
    fun `escaped quote inside quoted field is decoded`() = runTest {
        val catId = seedCategory()
        val csv = """
            date,type,amount,categoryId,categoryName,note,source
            2026-09-06T12:00:00Z,0,10.0,$catId,测试分类,"带""引号",手动
        """.trimIndent()

        val summary = useCase(csv)
        assertEquals(1, summary.imported)
        val list = repo.observeAllTransactions().first()
        assertEquals("带\"引号", list[0].note)
    }

    @Test
    fun `nonexistent categoryId is skipped`() = runTest {
        val catId = seedCategory()
        val csv = """
            date,type,amount,categoryId,categoryName,note,source
            2026-09-06T12:00:00Z,0,25.0,$catId,测试分类,合法,手动
            2026-09-06T13:00:00Z,0,30.0,9999,不存在的分类,跳过,手动
        """.trimIndent()

        val summary = useCase(csv)
        assertEquals(1, summary.imported)
        assertEquals(1, summary.skipped.size)
        assertEquals(3, summary.skipped[0].lineNo)
    }

    @Test
    fun `invalid amount is skipped`() = runTest {
        val catId = seedCategory()
        val csv = """
            date,type,amount,categoryId,categoryName,note,source
            2026-09-06T12:00:00Z,0,not_a_number,$catId,测试分类,坏,手动
            2026-09-06T13:00:00Z,0,-5.0,$catId,测试分类,负数,手动
        """.trimIndent()

        val summary = useCase(csv)
        assertEquals(0, summary.imported)
        assertEquals(2, summary.skipped.size)
    }

    @Test
    fun `invalid type is skipped`() = runTest {
        val catId = seedCategory()
        val csv = """
            date,type,amount,categoryId,categoryName,note,source
            2026-09-06T12:00:00Z,9,25.0,$catId,测试分类,非法类型,手动
        """.trimIndent()

        val summary = useCase(csv)
        assertEquals(0, summary.imported)
        assertEquals(1, summary.skipped.size)
    }

    @Test
    fun `missing required header throws`() = runTest {
        val csv = """
            date,amount,categoryId
            2026-09-06T12:00:00Z,25.0,1
        """.trimIndent()
        try {
            useCase(csv)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("type"))
        }
    }

    @Test
    fun `epoch millis date also accepted`() = runTest {
        val catId = seedCategory()
        val csv = """
            date,type,amount,categoryId,categoryName,note,source
            1725627296000,0,15.0,$catId,测试分类,epoch,手动
        """.trimIndent()

        val summary = useCase(csv)
        assertEquals(1, summary.imported)
    }
}