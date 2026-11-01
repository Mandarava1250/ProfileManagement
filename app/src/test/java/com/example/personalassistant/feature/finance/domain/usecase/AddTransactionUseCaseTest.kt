package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 7a：AddTransactionUseCase 单元测试
 *
 * 覆盖：
 * - amount 必须 > 0（IllegalArgumentException）
 * - categoryId 必须 > 0（IllegalArgumentException）
 * - 委托 Repository.addTransaction，返回落库后的 id
 * - note 空白字符串被剔除
 * - 写后 observeAllTransactions 可见
 */
class AddTransactionUseCaseTest {

    private lateinit var repo: FakeFinanceRepository
    private lateinit var useCase: AddTransactionUseCase

    @Before
    fun setup() {
        repo = FakeFinanceRepository()
        useCase = AddTransactionUseCase(repo)
    }

    @Test
    fun `valid input delegates to repository and returns id`() = runTest {
        val id = useCase(
            type = TransactionType.EXPENSE,
            amount = 25.5,
            categoryId = 1L,
            note = "午饭",
            date = 1000L,
        )
        assertTrue("expected id > 0, got $id", id > 0L)
        val list = repo.observeAllTransactions().first()
        assertEquals(1, list.size)
        assertEquals(25.5, list[0].amount, 0.0)
        assertEquals(TransactionType.EXPENSE, list[0].type)
        assertEquals("午饭", list[0].note)
    }

    @Test
    fun `zero amount is rejected`() = runTest {
        try {
            useCase(TransactionType.EXPENSE, 0.0, 1L, null, 1000L)
            fail("Expected IllegalArgumentException for amount = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("金额"))
        }
        assertEquals(0, repo.observeAllTransactions().first().size)
    }

    @Test
    fun `negative amount is rejected`() = runTest {
        try {
            useCase(TransactionType.EXPENSE, -1.0, 1L, null, 1000L)
            fail("Expected IllegalArgumentException for amount < 0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `invalid category id is rejected`() = runTest {
        try {
            useCase(TransactionType.EXPENSE, 10.0, 0L, null, 1000L)
            fail("Expected IllegalArgumentException for categoryId = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("分类"))
        }
        try {
            useCase(TransactionType.EXPENSE, 10.0, -1L, null, 1000L)
            fail("Expected IllegalArgumentException for categoryId < 0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `blank note is normalized to null`() = runTest {
        useCase(TransactionType.EXPENSE, 10.0, 1L, "   ", 1000L)
        val tx = repo.observeAllTransactions().first()[0]
        assertNull(tx.note)
    }

    @Test
    fun `income type is stored correctly`() = runTest {
        useCase(TransactionType.INCOME, 100.0, 2L, "工资", 2000L)
        val tx = repo.observeAllTransactions().first()[0]
        assertEquals(TransactionType.INCOME, tx.type)
    }
}
