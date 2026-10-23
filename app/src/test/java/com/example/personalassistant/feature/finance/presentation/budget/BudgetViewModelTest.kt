package com.example.personalassistant.feature.finance.presentation.budget

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.usecase.ObserveAllBudgetsUseCase
import com.example.personalassistant.feature.finance.domain.usecase.SetMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.presentation.util.Feedback
import com.example.personalassistant.feature.settings.FinanceCutoffSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 *
 * C 重构阶段 2/4 新增：
 * - save with invalid amount emits Warning feedback（不再 silently return）
 * - save with blank threshold persists 0_0 to repo（null 落 0.0 仍正确）
 *
 * 月截止日新增：
 * - 截止日输入框状态 / 范围校验 / 写入设置（用内存 fake，零 IO，行为确定）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeFinanceRepository
    private lateinit var settings: FakeFinanceCutoffSettings
    private lateinit var vm: BudgetViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeFinanceRepository()
        settings = FakeFinanceCutoffSettings()
        vm = BudgetViewModel(
            observeAllBudgets = ObserveAllBudgetsUseCase(repo),
            setMonthlyBudget = SetMonthlyBudgetUseCase(repo),
            settingsRepository = settings,
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

    /**
     * C 重构后："空字符串 ⇒ null 传 use case ⇒ Impl 落 0.0"。
     * 测试观察的是**仓库层**结果，仍然是 0.0；这条用例只是为语义改名。
     */
    @Test
    fun `save with blank threshold persists 0_0 via nullable`() = runTest(testDispatcher) {
        vm.setAmount("3000")
        vm.setWarningThreshold("")
        advanceUntilIdle()
        vm.save(onDone = {})
        advanceUntilIdle()

        val current = repo.observeCurrentMonthBudget().first()
        assertNotNull(current)
        // null 经 Impl 转 0.0 落库，与旧版本行为对齐
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

    /**
     * C 重构阶段 2 新增：非法金额 → Feedback.Warning，不再 silently return。
     * 同时验证 onDone 没被触发（保存失败时不关 sheet）。
     */
    @Test
    fun `save with invalid amount emits Warning feedback`() = runTest(testDispatcher) {
        vm.setAmount("abc")
        advanceUntilIdle()
        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        advanceUntilIdle()

        val fb = vm.feedback.value
        assertNotNull("应当收到反馈", fb)
        assertTrue("应当是 Warning, got $fb", fb is Feedback.Warning)
        assertTrue("Warning 文案应说明金额格式, got ${(fb as Feedback.Warning).message}",
            (fb).message.contains("金额"))
        assertEquals(false, doneCalled)
        // 不应写入 repo
        val current = repo.observeCurrentMonthBudget().first()
        assertNull("非法金额不应创建预算行", current)
    }

    // Phase 13：月截止日的用例已迁到
    // `com.example.personalassistant.feature.settings.MonthCutoffViewModelTest`
    // —— 截止日读写搬到了设置页的「记账」分组，不再由预算 VM 负责。
    // 本 VM 只保留 [BudgetViewModel.cutoffPolicy] 的读（用于展示账期区间）。
}

/**
 * 内存版 [FinanceCutoffSettings]
 *
 * 零 IO，所以 `runTest` 的虚拟时间能完整推进 —— 行为完全确定，不会 flaky。
 */
private class FakeFinanceCutoffSettings : FinanceCutoffSettings {

    val day = MutableStateFlow(BillingCycle.DEFAULT_CUTOFF_DAY)
    val effectiveFrom = MutableStateFlow(MonthCutoffPolicy.DEFAULT_EFFECTIVE_FROM)

    /** 写了几次，用来验证"没变就不重写" */
    var writeCount = 0
        private set

    override val financeMonthCutoffDay: Flow<Int> = day
    override val financeMonthCutoffEffectiveFrom: Flow<String> = effectiveFrom

    override suspend fun setFinanceMonthCutoff(day: Int, effectiveFromYm: String) {
        writeCount++
        this.day.value = day
        this.effectiveFrom.value = effectiveFromYm
    }
}
