package com.example.personalassistant.feature.settings

import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Phase 13：MonthCutoffViewModel 单元测试
 *
 * 覆盖月截止日设置（设置页「记账」分组）的全部行为：
 * - 草稿状态：默认空串、输入更新、[draftCutoffDay] 的合法/非法判定
 * - 范围校验：1-28 之外或非数字 → 给出提示且**不写设置**
 * - 写入：生效 ym = 当前账期（不是下个月）
 * - 不变量：值没变就不写 —— 写了会连带重算"生效 ym"，把冻结点往前推
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthCutoffViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settings: FakeFinanceCutoffSettings
    private lateinit var vm: MonthCutoffViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settings = FakeFinanceCutoffSettings()
        vm = MonthCutoffViewModel(settingsRepository = settings)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `draft defaults to blank`() = runTest(testDispatcher) {
        assertEquals("", vm.cutoffDayText.value)
        assertNull(vm.draftCutoffDay())
    }

    @Test
    fun `setCutoffDayText updates draft and draftCutoffDay parses it`() = runTest(testDispatcher) {
        vm.setCutoffDayText("15")
        advanceUntilIdle()

        assertEquals("15", vm.cutoffDayText.value)
        assertEquals(15, vm.draftCutoffDay())
    }

    @Test
    fun `draftCutoffDay is null for out of range or non numeric`() = runTest(testDispatcher) {
        vm.setCutoffDayText("29")
        assertNull(vm.draftCutoffDay())

        vm.setCutoffDayText("0")
        assertNull(vm.draftCutoffDay())

        vm.setCutoffDayText("abc")
        assertNull(vm.draftCutoffDay())

        vm.setCutoffDayText("")
        assertNull(vm.draftCutoffDay())
    }

    @Test
    fun `save writes cutoff day and effective ym is current cycle`() = runTest(testDispatcher) {
        vm.setCutoffDayText("15")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertEquals(15, settings.day.value)
        // 生效 ym = 当前账期（effectiveFromFor(ym) 就是 ym 本身）
        assertEquals(vm.currentCycleYm, settings.effectiveFrom.value)
        assertNotNull(vm.message.value)
        assertTrue(vm.message.value!!.contains("15"))
    }

    @Test
    fun `save with unchanged value does not rewrite the setting`() = runTest(testDispatcher) {
        settings.day.value = 15
        settings.effectiveFrom.value = "2026-10"

        vm.setCutoffDayText("15")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        // 截止日没变 → 不该重写"生效 ym"
        assertEquals("2026-10", settings.effectiveFrom.value)
        assertEquals(0, settings.writeCount)
    }

    @Test
    fun `save with invalid value reports message and does not write`() = runTest(testDispatcher) {
        vm.setCutoffDayText("29")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertNotNull(vm.message.value)
        assertTrue(vm.message.value!!.contains("截止日"))
        assertEquals(0, settings.writeCount)
        assertEquals(BillingCycle.DEFAULT_CUTOFF_DAY, settings.day.value)
    }

    @Test
    fun `save with blank draft reports message and does not write`() = runTest(testDispatcher) {
        vm.save()
        advanceUntilIdle()

        assertNotNull(vm.message.value)
        assertEquals(0, settings.writeCount)
    }

    @Test
    fun `successful save clears the draft`() = runTest(testDispatcher) {
        vm.setCutoffDayText("15")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertEquals("", vm.cutoffDayText.value)
    }
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
