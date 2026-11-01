package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Phase 7a：DeleteTransactionUseCase 单元测试
 *
 * 覆盖：
 * - 委托 Repository.deleteTransaction
 * - 不存在的 id 静默 no-op（不抛异常）
 * - 删除后 observe 流不再发射该条
 */
class DeleteTransactionUseCaseTest {

    private lateinit var repo: FakeFinanceRepository
    private lateinit var useCase: DeleteTransactionUseCase

    @Before
    fun setup() {
        repo = FakeFinanceRepository()
        useCase = DeleteTransactionUseCase(repo)
    }

    @Test
    fun `deleting existing transaction removes it from flow`() = runTest {
        val id = repo.addTransaction(
            type = com.example.personalassistant.feature.finance.domain.model.TransactionType.EXPENSE,
            amount = 10.0,
            categoryId = 1L,
            note = null,
            date = 1000L,
        )
        assertEquals(1, repo.observeAllTransactions().first().size)

        useCase(id)

        assertEquals(0, repo.observeAllTransactions().first().size)
    }

    @Test
    fun `deleting non-existent id is a silent no-op`() = runTest {
        repo.addTransaction(
            type = com.example.personalassistant.feature.finance.domain.model.TransactionType.EXPENSE,
            amount = 10.0,
            categoryId = 1L,
            note = null,
            date = 1000L,
        )
        // 不存在的 id 不应抛异常
        useCase(9999L)
        assertEquals(1, repo.observeAllTransactions().first().size)
    }

    @Test
    fun `deleting one transaction does not affect others`() = runTest {
        val id1 = repo.addTransaction(
            type = com.example.personalassistant.feature.finance.domain.model.TransactionType.EXPENSE,
            amount = 10.0,
            categoryId = 1L,
            note = "a",
            date = 1000L,
        )
        val id2 = repo.addTransaction(
            type = com.example.personalassistant.feature.finance.domain.model.TransactionType.EXPENSE,
            amount = 20.0,
            categoryId = 1L,
            note = "b",
            date = 2000L,
        )
        useCase(id1)
        val list = repo.observeAllTransactions().first()
        assertEquals(1, list.size)
        assertEquals(id2, list[0].id)
    }
}
