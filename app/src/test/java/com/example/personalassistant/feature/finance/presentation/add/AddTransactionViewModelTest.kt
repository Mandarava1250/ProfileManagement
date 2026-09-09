package com.example.personalassistant.feature.finance.presentation.add

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.usecase.AddTransactionUseCase
import com.example.personalassistant.feature.finance.domain.usecase.DeleteTransactionUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCategoriesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.UpdateTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 7b：AddTransactionViewModel 单元测试
 *
 * 覆盖：
 * - setAmount 解析字符串为 Double
 * - setType 切换会清空 categoryId（避免类型不匹配遗留）
 * - save 缺 categoryId 时不调 UseCase
 * - save 新增模式 → addTransaction
 * - loadForEdit + save → updateTransaction（编辑模式）
 * - deleteCurrent → DeleteTransactionUseCase
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeFinanceRepository
    private lateinit var vm: AddTransactionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeFinanceRepository()
        vm = AddTransactionViewModel(
            observeCategories = ObserveCategoriesUseCase(repo),
            addTransaction = AddTransactionUseCase(repo),
            updateTransaction = UpdateTransactionUseCase(repo),
            deleteTransaction = DeleteTransactionUseCase(repo),
            attachmentStore = FakeAttachmentStore(testDispatcher),
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setAmount parses numeric string`() = runTest(testDispatcher) {
        vm.setAmount("42.5")
        assertEquals("42.5", vm.state.value.amountText)
        assertEquals(42.5, vm.state.value.amount, 0.001)
    }

    @Test
    fun `setAmount with non-numeric becomes zero`() = runTest(testDispatcher) {
        vm.setAmount("abc")
        assertEquals(0.0, vm.state.value.amount, 0.001)
    }

    @Test
    fun `setType clears categoryId`() = runTest(testDispatcher) {
        vm.setCategory(7L)
        vm.setType(TransactionType.INCOME)
        assertNull(vm.state.value.categoryId)
        assertEquals(TransactionType.INCOME, vm.state.value.type)
    }

    @Test
    fun `save without categoryId does not invoke add`() = runTest(testDispatcher) {
        vm.setAmount("30.0")
        vm.save(onDone = {})
        advanceUntilIdle()
        assertTrue("No transaction should be added", repo.observeAllTransactions().first().isEmpty())
    }

    @Test
    fun `save with categoryId invokes addTransaction path`() = runTest(testDispatcher) {
        val catId = repo.seedExpenseCategory("午餐")
        vm.setType(TransactionType.EXPENSE)
        vm.setCategory(catId)
        vm.setAmount("25.0")
        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        advanceUntilIdle()
        val txs = repo.observeAllTransactions().first()
        assertEquals(1, txs.size)
        assertEquals(25.0, txs[0].amount, 0.001)
        assertTrue("onDone should be called after successful save", doneCalled)
    }

    @Test
    fun `loadForEdit populates state then save updates existing transaction`() = runTest(testDispatcher) {
        val catId = repo.seedExpenseCategory("原分类")
        repo.seedTransaction(TransactionType.EXPENSE, 100.0, catId, "原备注", 1000L)
        val existing = repo.observeAllTransactions().first().first()

        // load for edit
        vm.loadForEdit(existing)
        advanceUntilIdle()
        assertEquals(100.0, vm.state.value.amount, 0.001)
        assertEquals(catId, vm.state.value.categoryId)

        // 修改金额并 save
        vm.setAmount("150.0")
        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        advanceUntilIdle()

        val updated = repo.observeAllTransactions().first()
        assertEquals(1, updated.size)
        assertEquals(150.0, updated[0].amount, 0.001)
        assertEquals(existing.id, updated[0].id)
        assertTrue(doneCalled)
    }

    @Test
    fun `deleteCurrent removes editing transaction`() = runTest(testDispatcher) {
        val catId = repo.seedExpenseCategory("c")
        repo.seedTransaction(TransactionType.EXPENSE, 50.0, catId, null, 2000L)
        val existing = repo.observeAllTransactions().first().first()

        vm.loadForEdit(existing)
        advanceUntilIdle()
        var doneCalled = false
        vm.deleteCurrent(onDone = { doneCalled = true })
        advanceUntilIdle()
        assertTrue(repo.observeAllTransactions().first().isEmpty())
        assertTrue(doneCalled)
    }
}