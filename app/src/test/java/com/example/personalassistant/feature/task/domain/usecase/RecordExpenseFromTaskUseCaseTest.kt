package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.task.domain.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 21：从清单项一键记账 单测
 *
 * 用 [FakeFinanceRepository]（记账模块的公共假仓库）当替身 ——
 * 它会真的把流水存进 `transactionsState`，所以断言能直接看落库结果。
 */
class RecordExpenseFromTaskUseCaseTest {

    private fun task(amount: Double?, dueDate: Long? = null) = Task(
        id = 7L,
        title = "买牛奶",
        amount = amount,
        dueDate = dueDate,
    )

    @Test
    fun `没有金额时抛错 而不是记一笔 0 元`() = runTest {
        val repo = FakeFinanceRepository()
        val useCase = RecordExpenseFromTaskUseCase(repo)

        val error = runCatching {
            useCase(task(amount = null), categoryId = 1L)
        }.exceptionOrNull()

        assertNotNull("没有金额应该抛错", error)
        assertTrue("不该写进流水表", repo.observeAllTransactions().first().isEmpty())
    }

    @Test
    fun `金额为 0 时抛错`() = runTest {
        val repo = FakeFinanceRepository()
        val useCase = RecordExpenseFromTaskUseCase(repo)

        val error = runCatching {
            useCase(task(amount = 0.0), categoryId = 1L)
        }.exceptionOrNull()

        assertNotNull("0 元应该抛错", error)
        assertTrue(repo.observeAllTransactions().first().isEmpty())
    }

    @Test
    fun `负数金额抛错`() = runTest {
        val repo = FakeFinanceRepository()
        val useCase = RecordExpenseFromTaskUseCase(repo)

        val error = runCatching {
            useCase(task(amount = -5.0), categoryId = 1L)
        }.exceptionOrNull()

        assertNotNull("负数金额应该抛错", error)
        assertTrue(repo.observeAllTransactions().first().isEmpty())
    }

    @Test
    fun `正常记账 写的是支出流水`() = runTest {
        val repo = FakeFinanceRepository()
        val useCase = RecordExpenseFromTaskUseCase(repo)

        useCase(task(amount = 9.9), categoryId = 3L)

        val tx = repo.observeAllTransactions().first().single()
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(9.9, tx.amount, 0.0001)
        assertEquals(3L, tx.categoryId)
        assertEquals("买牛奶", tx.note)
    }

    @Test
    fun `流水指回清单项`() = runTest {
        val repo = FakeFinanceRepository()
        val useCase = RecordExpenseFromTaskUseCase(repo)

        useCase(task(amount = 9.9), categoryId = 3L)

        val tx = repo.observeAllTransactions().first().single()
        assertEquals("流水应记下来源清单项 id", 7L, tx.linkedTaskId)
    }

    @Test
    fun `没给日期时用清单项的截止时间`() = runTest {
        val repo = FakeFinanceRepository()
        val useCase = RecordExpenseFromTaskUseCase(repo)

        useCase(task(amount = 9.9, dueDate = 1_700_000_000_000L), categoryId = 3L)

        assertEquals(1_700_000_000_000L, repo.observeAllTransactions().first().single().date)
    }

    @Test
    fun `显式给的日期优先于截止时间`() = runTest {
        val repo = FakeFinanceRepository()
        val useCase = RecordExpenseFromTaskUseCase(repo)

        useCase(
            task(amount = 9.9, dueDate = 1_700_000_000_000L),
            categoryId = 3L,
            date = 1_800_000_000_000L,
        )

        assertEquals(1_800_000_000_000L, repo.observeAllTransactions().first().single().date)
    }

    @Test
    fun `既没给日期也没有截止时间时用当前时间`() = runTest {
        val repo = FakeFinanceRepository()
        val useCase = RecordExpenseFromTaskUseCase(repo)

        val before = System.currentTimeMillis()
        useCase(task(amount = 9.9), categoryId = 3L)
        val after = System.currentTimeMillis()

        val used = repo.observeAllTransactions().first().single().date
        assertTrue("应落在调用前后之间", used in before..after)
    }
}
