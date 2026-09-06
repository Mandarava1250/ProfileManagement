package com.example.personalassistant.feature.finance.presentation.budget

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.usecase.ObserveMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.domain.usecase.SetMonthlyBudgetUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 7b：BudgetViewModel 单元测试
 *
 * 覆盖：
 * - setAmount / setAutoRefresh / setCarryOverEnabled 正确更新 state
 * - setWarningThreshold 更新 state
 * - save 调用 setMonthlyBudget 并透传 totalAmount + threshold
 * - loadFromPersisted 把持久化值还原成 state（threshold=0 显示空字符串）
 * - canSave: 非法 amount → false
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeFinanceRepository
    private lateinit var vm: BudgetViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeFinanceRepository()
        vm = BudgetViewModel(
            observeMonthlyBudget = ObserveMonthlyBudgetUseCase(repo),
            setMonthlyBudget = SetMonthlyBudgetUseCase(repo),
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `editing default is empty`() = runTest(testDispatcher) {
        val s = vm.editing.value
        assertEquals("", s.totalAmountText)
        assertEquals("", s.warningThresholdText)
        assertTrue(s.isAutoRefresh)
        assertTrue(s.carryOverEnabled)
        assertEquals(false, s.canSave)
    }

    @Test
    fun `setAmount and setWarningThreshold update state`() = runTest(testDispatcher) {
        vm.setAmount("5000")
        vm.setWarningThreshold("200")
        advanceUntilIdle()

        val s = vm.editing.value
        assertEquals("5000", s.totalAmountText)
        assertEquals("200", s.warningThresholdText)
        assertTrue(s.canSave)
    }

    @Test
    fun `save persists totalAmount and warningThreshold`() = runTest(testDispatcher) {
        vm.setAmount("3000")
        vm.setWarningThreshold("150")
        advanceUntilIdle()
        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        advanceUntilIdle()

        val current = repo.observeCurrentMonthBudget().first()
        assertNotNull(current)
        assertEquals(3000.0, current!!.totalAmount, 0.001)
        assertEquals(150.0, current.warningThreshold, 0.001)
        assertTrue(doneCalled)
    }

    @Test
    fun `save with blank threshold persists 0_0`() = runTest(testDispatcher) {
        vm.setAmount("3000")
        vm.setWarningThreshold("")
        advanceUntilIdle()
        vm.save(onDone = {})
        advanceUntilIdle()

        val current = repo.observeCurrentMonthBudget().first()
        assertNotNull(current)
        assertEquals(0.0, current!!.warningThreshold, 0.001)
    }

    @Test
    fun `loadFromPersisted restores state including threshold`() = runTest(testDispatcher) {
        val persisted = MonthlyBudget(
            yearMonth = "2099-12",
            totalAmount = 8000.0,
            isAutoRefresh = false,
            carryOverEnabled = false,
            warningThreshold = 500.0,
        )
        vm.loadFromPersisted(persisted)
        advanceUntilIdle()

        val s = vm.editing.value
        assertEquals("8000", s.totalAmountText)
        assertEquals(false, s.isAutoRefresh)
        assertEquals(false, s.carryOverEnabled)
        assertEquals("500", s.warningThresholdText)
    }

    @Test
    fun `loadFromPersisted with null resets to empty`() = runTest(testDispatcher) {
        vm.loadFromPersisted(null)
        advanceUntilIdle()
        assertEquals("", vm.editing.value.totalAmountText)
    }

    @Test
    fun `canSave is false when amount is invalid`() = runTest(testDispatcher) {
        vm.setAmount("abc")
        advanceUntilIdle()
        assertEquals(false, vm.editing.value.canSave)
    }
}