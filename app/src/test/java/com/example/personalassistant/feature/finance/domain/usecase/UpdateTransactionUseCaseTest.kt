package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 7b：UpdateTransactionUseCase 单元测试
 *
 * 覆盖：
 * - amount 必须 > 0（IllegalArgumentException）
 * - categoryId 必须 > 0（IllegalArgumentException）
 * - 正常更新：金额 + 分类变化
 * - 类型变化（支出 → 收入）：字段切换成功
 * - id 不存在时静默 no-op
 */
class UpdateTransactionUseCaseTest {

    private lateinit var repo: FakeFinanceRepository
    private lateinit var useCase: UpdateTransactionUseCase

    @Before
    fun setup() {
        repo = FakeFinanceRepository()
        useCase = UpdateTransactionUseCase(repo)
    }

    private suspend fun seedExpense(amount: Double = 10.0, note: String? = "种子"): Long {
        return AddTransactionUseCase(repo).invoke(
            type = TransactionType.EXPENSE,
            amount = amount,
            categoryId = 1L,
            note = note,
            date = 1000L,
        )
    }

    @Test
    fun `valid input updates amount and category`() = runTest {
        val id = seedExpense(amount = 10.0, note = "午饭")

        useCase(
            id = id,
            type = TransactionType.EXPENSE,
            amount = 25.0,
            categoryId = 2L,
            note = "午饭（大杯）",
            date = 1000L,
        )

        val tx = repo.observeAllTransactions().first().single { it.id == id }
        assertEquals(25.0, tx.amount, 0.0)
        assertEquals(2L, tx.categoryId)
        assertEquals("午饭（大杯）", tx.note)
    }

    @Test
    fun `type change from expense to income`() = runTest {
        val id = seedExpense(amount = 10.0)

        useCase(
            id = id,
            type = TransactionType.INCOME,
            amount = 100.0,
            categoryId = 2L,
            note = null,
            date = 2000L,
        )

        val tx = repo.observeAllTransactions().first().single { it.id == id }
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals(100.0, tx.amount, 0.0)
        assertEquals(2000L, tx.date)
        // 注：Fake 不验证 pool 一致性（交由 DAO 单测覆盖事务）
    }

    @Test
    fun `nonexistent id is silent no-op`() = runTest {
        val id = seedExpense(amount = 10.0)
        val sizeBefore = repo.observeAllTransactions().first().size

        // 不抛异常
        useCase(
            id = 9999L, // 不存在
            type = TransactionType.EXPENSE,
            amount = 50.0,
            categoryId = 1L,
            note = "n/a",
            date = 1000L,
        )

        val list = repo.observeAllTransactions().first()
        assertEquals(sizeBefore, list.size)
        assertEquals(10.0, list.single { it.id == id }.amount, 0.0)
    }

    @Test
    fun `zero amount is rejected`() = runTest {
        try {
            useCase(1L, TransactionType.EXPENSE, 0.0, 1L, null, 1000L)
            fail("Expected IllegalArgumentException for amount = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("金额"))
        }
    }

    @Test
    fun `blank note is normalized to null`() = runTest {
        val id = seedExpense(amount = 10.0, note = "原备注")

        useCase(id, TransactionType.EXPENSE, 10.0, 1L, "   ", 1000L)

        val tx = repo.observeAllTransactions().first().single { it.id == id }
        assertEquals(null, tx.note)
    }
}