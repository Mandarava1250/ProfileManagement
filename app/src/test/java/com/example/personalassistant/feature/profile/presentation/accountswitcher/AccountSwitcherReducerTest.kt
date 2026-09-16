package com.example.personalassistant.feature.profile.presentation.accountswitcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9b B2：账户切换条 reducer 纯函数测试。
 *
 * 全部同步 —— reducer 不碰协程、不碰 Repository，所以测试也不该引入调度器。
 * 这里覆盖的是「状态迁移本身正确」，VM 的 Flow 组装由 AccountSwitcherViewModelTest 负责。
 */
class AccountSwitcherReducerTest {

    private val accountA = AccountSwitcherItem(id = 1L, name = "默认账户")
    private val accountB = AccountSwitcherItem(id = 2L, name = "旅行账本")
    private val accountC = AccountSwitcherItem(id = 3L, name = "副卡")

    private val loaded = AccountSwitcherState(
        isLoading = false,
        accounts = listOf(accountA, accountB, accountC),
        currentAccountId = 1L,
    )

    // ===== LoadStarted =====

    @Test
    fun `LoadStarted 置为加载中并清掉旧错误`() {
        val dirty = loaded.copy(errorMessage = "上一次失败")

        val next = reduceAccountSwitcher(dirty, AccountSwitcherAction.LoadStarted)

        assertTrue(next.isLoading)
        assertNull(next.errorMessage)
    }

    // ===== AccountsLoaded =====

    @Test
    fun `AccountsLoaded 装入清单并结束加载`() {
        val next = reduceAccountSwitcher(
            AccountSwitcherState(),
            AccountSwitcherAction.AccountsLoaded(
                accounts = listOf(accountA, accountB),
                currentAccountId = 2L,
            ),
        )

        assertFalse(next.isLoading)
        assertEquals(listOf(accountA, accountB), next.accounts)
        assertEquals(2L, next.currentAccountId)
        assertFalse(next.isSwitching)
    }

    @Test
    fun `AccountsLoaded 遇到悬空 currentId 时回落到第一个账户`() {
        // 场景：Settings 页删掉了当前账户，DataStore 里还留着指向它的 id。
        // 若不兜底，切换条会把「不存在的账户」画成选中态 —— 用户看到没有任何 Chip 被选中。
        val next = reduceAccountSwitcher(
            AccountSwitcherState(),
            AccountSwitcherAction.AccountsLoaded(
                accounts = listOf(accountB, accountC),
                currentAccountId = 99L,
            ),
        )

        assertEquals(2L, next.currentAccountId)
    }

    @Test
    fun `AccountsLoaded 空清单时保留原 id 而不是崩掉`() {
        val next = reduceAccountSwitcher(
            loaded,
            AccountSwitcherAction.AccountsLoaded(
                accounts = emptyList(),
                currentAccountId = 1L,
            ),
        )

        assertEquals(emptyList<AccountSwitcherItem>(), next.accounts)
        assertEquals(1L, next.currentAccountId)
        assertFalse(next.isVisible)
    }

    @Test
    fun `AccountsLoaded 收敛 isSwitching 避免一直转圈`() {
        // 场景：写 DataStore 成功但 Flow 回流慢于回调，此时仍是 isSwitching=true。
        // 清单一旦回流就代表「数据层已经认了」，转圈状态必须收敛。
        val switching = loaded.copy(isSwitching = true)

        val next = reduceAccountSwitcher(
            switching,
            AccountSwitcherAction.AccountsLoaded(
                accounts = listOf(accountA, accountB),
                currentAccountId = 1L,
            ),
        )

        assertFalse(next.isSwitching)
    }

    // ===== SwitchRequested =====

    @Test
    fun `SwitchRequested 切到别的账户时进入切换中`() {
        val next = reduceAccountSwitcher(loaded, AccountSwitcherAction.SwitchRequested(2L))

        assertTrue(next.isSwitching)
        assertEquals(1L, next.currentAccountId) // 未确认前不动选中态
    }

    @Test
    fun `SwitchRequested 点当前账户是 no-op`() {
        val next = reduceAccountSwitcher(loaded, AccountSwitcherAction.SwitchRequested(1L))

        assertEquals(loaded, next)
    }

    @Test
    fun `SwitchRequested 清掉上一次的错误`() {
        val dirty = loaded.copy(errorMessage = "上一次失败")

        val next = reduceAccountSwitcher(dirty, AccountSwitcherAction.SwitchRequested(2L))

        assertNull(next.errorMessage)
        assertTrue(next.isSwitching)
    }

    // ===== SwitchSucceeded / SwitchFailed =====

    @Test
    fun `SwitchSucceeded 乐观更新选中态并结束切换`() {
        val switching = loaded.copy(isSwitching = true)

        val next = reduceAccountSwitcher(switching, AccountSwitcherAction.SwitchSucceeded(3L))

        assertEquals(3L, next.currentAccountId)
        assertFalse(next.isSwitching)
        assertNull(next.errorMessage)
        assertEquals("副卡", next.currentAccountName)
    }

    @Test
    fun `SwitchFailed 记录错误并结束切换`() {
        val switching = loaded.copy(isSwitching = true)

        val next = reduceAccountSwitcher(switching, AccountSwitcherAction.SwitchFailed("磁盘写入失败"))

        assertEquals("磁盘写入失败", next.errorMessage)
        assertFalse(next.isSwitching)
        assertEquals(1L, next.currentAccountId) // 失败不改选中态
    }

    // ===== ErrorConsumed =====

    @Test
    fun `ErrorConsumed 清空错误提示`() {
        val dirty = loaded.copy(errorMessage = "切换账户失败")

        val next = reduceAccountSwitcher(dirty, AccountSwitcherAction.ErrorConsumed)

        assertNull(next.errorMessage)
    }

    // ===== 派生属性 =====

    @Test
    fun `isVisible 仅在加载完成且账户数大于等于 2 时为真`() {
        assertFalse("加载中不该显示", AccountSwitcherState().isVisible)
        assertFalse(
            "单账户没有切换意义",
            loaded.copy(accounts = listOf(accountA)).isVisible,
        )
        assertTrue(
            "多账户应显示",
            loaded.copy(accounts = listOf(accountA, accountB)).isVisible,
        )
    }

    @Test
    fun `currentAccountName 找不到时回落到空串`() {
        val next = loaded.copy(currentAccountId = 99L)

        assertEquals("", next.currentAccountName)
    }
}