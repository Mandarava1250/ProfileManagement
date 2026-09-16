package com.example.personalassistant.feature.finance.presentation.transfer

import com.example.personalassistant.feature.finance.domain.model.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9b D1：`TransferFormState` 的 JVM 单测。
 *
 * 转账表单的推导规则（自身排除 / 余额不足 / 可提交条件 / 摘要文案）
 * 全部在这里覆盖 —— 毫秒级，不用起 AVD。
 *
 * instrumented 侧（[TransferSheetContentTest]）只验「渲染 + 交互」，
 * 不重复验算这些规则。
 */
class TransferFormStateTest {

    private fun balance(id: Long, name: String, amount: Double) = AccountBalance(
        accountId = id,
        accountName = name,
        initialBalance = amount,
        income = 0.0,
        expense = 0.0,
        transferNet = 0.0,
    )

    private val twoAccounts = listOf(
        balance(1L, "默认账户", 1000.0),
        balance(2L, "旅行账本", 300.0),
    )

    // ===== 目标账户：自身排除 =====

    /** 当前账户不出现在转入列表（自己转自己 Repository 会拒，也不该摆在选项里） */
    @Test
    fun `excludes current account from targets`() {
        val state = TransferFormState.from(twoAccounts, currentAccountId = 1L)

        assertEquals(1, state.targets.size)
        assertEquals(2L, state.targets.first().accountId)
        assertEquals("旅行账本", state.targets.first().accountName)
    }

    /** 当前账户在列表里排在后面时，同样被排除 */
    @Test
    fun `excludes current account regardless of order`() {
        val state = TransferFormState.from(twoAccounts, currentAccountId = 2L)

        assertEquals(1, state.targets.size)
        assertEquals(1L, state.targets.first().accountId)
    }

    /** 只有当前账户一个：转入列表为空（转账无处可去） */
    @Test
    fun `single account yields no target`() {
        val state = TransferFormState.from(
            balances = listOf(balance(1L, "默认账户", 500.0)),
            currentAccountId = 1L,
        )

        assertTrue(state.hasNoTarget)
        assertFalse(state.canSubmit)
    }

    /** 三个账户：排除自身后剩两个 */
    @Test
    fun `three accounts leave two targets`() {
        val state = TransferFormState.from(
            balances = listOf(
                balance(1L, "A", 100.0),
                balance(2L, "B", 200.0),
                balance(3L, "C", 300.0),
            ),
            currentAccountId = 2L,
        )

        assertEquals(listOf(1L, 3L), state.targets.map { it.accountId })
    }

    // ===== 转出方信息 =====

    /** 转出方名字与余额从当前账户取 */
    @Test
    fun `captures from name and balance`() {
        val state = TransferFormState.from(twoAccounts, currentAccountId = 1L)

        assertEquals("默认账户", state.fromName)
        assertEquals(1000.0, state.fromBalance!!, 0.0001)
    }

    /** 当前账户不在余额列表里：名字与余额为 null（UI 走兜底文案） */
    @Test
    fun `missing current account yields null from info`() {
        val state = TransferFormState.from(twoAccounts, currentAccountId = 99L)

        assertNull(state.fromName)
        assertNull(state.fromBalance)
        // 全部两个账户都成了候选
        assertEquals(2, state.targets.size)
    }

    // ===== 金额解析 =====

    /** 空白 / 非数字 / 零 / 负数都不是合法金额 */
    @Test
    fun `invalid amount text yields null amount`() {
        val base = TransferFormState.from(twoAccounts, currentAccountId = 1L)

        assertNull(base.copy(amountText = "").amount)
        assertNull(base.copy(amountText = "   ").amount)
        assertNull(base.copy(amountText = "abc").amount)
        assertNull(base.copy(amountText = "0").amount)
        assertNull(base.copy(amountText = "-5").amount)
    }

    /** 合法金额（含前后空格）能解析 */
    @Test
    fun `valid amount text parses`() {
        val base = TransferFormState.from(twoAccounts, currentAccountId = 1L)

        assertEquals(12.5, base.copy(amountText = "12.5").amount!!, 0.0001)
        assertEquals(100.0, base.copy(amountText = " 100 ").amount!!, 0.0001)
    }

    // ===== 余额不足 =====

    /** 超出转出方余额 → overBalance，且不可提交 */
    @Test
    fun `over balance blocks submit`() {
        val state = TransferFormState.from(
            balances = twoAccounts,
            currentAccountId = 1L,
            amountText = "1500",
            targetId = 2L,
        )

        assertTrue(state.overBalance)
        assertFalse(state.canSubmit)
    }

    /** 恰好等于余额 → 不算超（边界：可以全部转出） */
    @Test
    fun `amount equal to balance is allowed`() {
        val state = TransferFormState.from(
            balances = twoAccounts,
            currentAccountId = 1L,
            amountText = "1000",
            targetId = 2L,
        )

        assertFalse(state.overBalance)
        assertTrue(state.canSubmit)
    }

    /** 余额未知时不判超（避免误报，真正的防线在 Repository） */
    @Test
    fun `unknown balance never reports over balance`() {
        val state = TransferFormState.from(
            balances = twoAccounts,
            currentAccountId = 99L,
            amountText = "99999",
            targetId = 2L,
        )

        assertFalse(state.overBalance)
    }

    // ===== 可提交条件 =====

    /** 四项缺一不可：目标 / 金额 / 未超余额 */
    @Test
    fun `can submit requires all conditions`() {
        val ok = TransferFormState.from(
            balances = twoAccounts,
            currentAccountId = 1L,
            amountText = "100",
            targetId = 2L,
        )
        assertTrue(ok.canSubmit)

        // 缺目标
        assertFalse(ok.copy(targetId = null).canSubmit)
        // 金额非法
        assertFalse(ok.copy(amountText = "").canSubmit)
        // 无候选账户
        assertFalse(ok.copy(targets = emptyList()).canSubmit)
        // 超余额
        assertFalse(ok.copy(amountText = "5000").canSubmit)
    }

    // ===== 摘要文案 =====

    /** 未选目标时不显示摘要 */
    @Test
    fun `no summary before target selected`() {
        val state = TransferFormState.from(
            balances = twoAccounts,
            currentAccountId = 1L,
            amountText = "100",
        )

        assertNull(state.confirmSummary())
        assertNull(state.selected)
    }

    /** 选中后摘要含转出方、金额、转入方 */
    @Test
    fun `summary shows flow direction`() {
        val state = TransferFormState.from(
            balances = twoAccounts,
            currentAccountId = 1L,
            amountText = "100",
            targetId = 2L,
        )

        assertEquals("旅行账本", state.selected?.accountName)
        assertEquals("确认后：默认账户 −100 → 旅行账本", state.confirmSummary())
    }

    /** 转出方未知时摘要用兜底名 */
    @Test
    fun `summary falls back when from unknown`() {
        val state = TransferFormState.from(
            balances = twoAccounts,
            currentAccountId = 99L,
            amountText = "50",
            targetId = 2L,
        )

        assertEquals("确认后：当前账户 −50 → 旅行账本", state.confirmSummary())
    }

    /** 选中一个已不在候选里的 id：selected 为 null（不崩、不误显示） */
    @Test
    fun `stale target id yields null selected`() {
        val state = TransferFormState.from(
            balances = twoAccounts,
            currentAccountId = 1L,
            amountText = "10",
            targetId = 777L,
        )

        assertNull(state.selected)
        assertNull(state.confirmSummary())
        // targetId 非空仍让 canSubmit 为 true —— 这是既有行为（Repository 会拒未知账户）
        assertTrue(state.canSubmit)
    }
}