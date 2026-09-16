package com.example.personalassistant.feature.profile.presentation.accountswitcher

import com.example.personalassistant.feature.profile.data.FakeAccountRepository
import com.example.personalassistant.feature.profile.data.FakeCurrentAccountRepository
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 9b B2：AccountSwitcherViewModel 测试。
 *
 * 覆盖 VM 真正多出来的那层逻辑 —— **Flow 组装与写路径**：
 * - `observeAccounts()` + `currentAccountIdFlow()` 合流后是否正确落到 state
 * - `switchTo` 是否真的调了 `setCurrentAccountId`（而不只是改本地 state）
 * - 写失败时是否转成 errorMessage 而不是崩掉
 * - 点当前账户是否**不产生写**（no-op 的边界）
 *
 * 状态迁移的细节由 [AccountSwitcherReducerTest] 覆盖，这里不重复。
 *
 * 用项目既有的 [FakeAccountRepository] / [FakeCurrentAccountRepository]（「Fake 优先、无 Mockito」约定）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitcherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val accountA = UserProfileEntity(id = 1L, name = "默认账户")
    private val accountB = UserProfileEntity(id = 2L, name = "旅行账本")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(
        accounts: List<UserProfileEntity> = listOf(accountA, accountB),
        current: FakeCurrentAccountRepository = FakeCurrentAccountRepository(initialAccountId = 1L),
    ): Pair<AccountSwitcherViewModel, FakeCurrentAccountRepository> {
        val vm = AccountSwitcherViewModel(
            accountRepository = FakeAccountRepository(accounts),
            currentAccountRepository = current,
        )
        return vm to current
    }

    // ===== Flow 组装 =====

    @Test
    fun `初始状态是加载中`() {
        val (vm, _) = buildVm()

        assertTrue(vm.state.value.isLoading)
        assertTrue(vm.state.value.accounts.isEmpty())
    }

    @Test
    fun `账户清单与当前账户回流后落到 state`() = runTest(testDispatcher) {
        val (vm, _) = buildVm()

        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(1L, 2L), state.accounts.map { it.id })
        assertEquals(listOf("默认账户", "旅行账本"), state.accounts.map { it.name })
        assertEquals(1L, state.currentAccountId)
        assertTrue(state.isVisible)
    }

    @Test
    fun `清单回流时同步带入头像字段`() = runTest(testDispatcher) {
        val withAvatar = accountB.copy(avatarUri = "file:///avatar/b.jpg")
        val (vm, _) = buildVm(accounts = listOf(accountA, withAvatar))

        advanceUntilIdle()

        assertEquals("file:///avatar/b.jpg", vm.state.value.accounts[1].avatarUri)
    }

    @Test
    fun `单账户时 isVisible 为假`() = runTest(testDispatcher) {
        val (vm, _) = buildVm(accounts = listOf(accountA))

        advanceUntilIdle()

        assertFalse(vm.state.value.isVisible)
    }

    // ===== 写路径 =====

    @Test
    fun `switchTo 真的写入了当前账户偏好`() = runTest(testDispatcher) {
        val (vm, current) = buildVm()
        advanceUntilIdle()

        vm.switchTo(2L)
        advanceUntilIdle()

        assertEquals("必须落库，而不是只改本地 state", 1, current.setCallCount)
        assertEquals(2L, current.lastSetAccountId)
        assertEquals(2L, vm.state.value.currentAccountId)
        assertFalse(vm.state.value.isSwitching)
    }

    @Test
    fun `switchTo 当前账户不产生写`() = runTest(testDispatcher) {
        val (vm, current) = buildVm()
        advanceUntilIdle()

        vm.switchTo(1L) // 已经是当前账户
        advanceUntilIdle()

        assertEquals("点当前账户不该写 DataStore", 0, current.setCallCount)
        assertEquals(1L, vm.state.value.currentAccountId)
    }

    @Test
    fun `switchTo 之后 currentAccountIdFlow 回流不会把状态带回去`() = runTest(testDispatcher) {
        // 这条盯的是「乐观更新」与「上游回流」的一致性：
        // 若 reducer 在 AccountsLoaded 里无脑覆盖 currentAccountId，
        // 回流晚于回调时用户会看到 Chip 选中态弹回旧账户。
        val current = FakeCurrentAccountRepository(initialAccountId = 1L)
        val (vm, _) = buildVm(current = current)
        advanceUntilIdle()

        vm.switchTo(2L)
        advanceUntilIdle()

        assertEquals(2L, vm.state.value.currentAccountId)
        assertEquals(2L, current.currentAccountId())
    }

    // ===== 失败路径 =====

    @Test
    fun `写失败时转成 errorMessage 且不改选中态`() = runTest(testDispatcher) {
        val failing = object : com.example.personalassistant.feature.profile.data.CurrentAccountRepository {
            private val state = MutableStateFlow(1L)
            override suspend fun currentAccountId(): Long = state.value
            override fun currentAccountIdFlow(): Flow<Long> = state
            override suspend fun setCurrentAccountId(accountId: Long) {
                throw IllegalStateException("磁盘写入失败")
            }
        }
        val vm = AccountSwitcherViewModel(
            accountRepository = FakeAccountRepository(listOf(accountA, accountB)),
            currentAccountRepository = failing,
        )
        advanceUntilIdle()

        vm.switchTo(2L)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("磁盘写入失败", state.errorMessage)
        assertFalse(state.isSwitching)
        assertEquals("失败时选中态不该跟着动", 1L, state.currentAccountId)
    }

    @Test
    fun `consumeError 清空错误提示`() = runTest(testDispatcher) {
        val failing = object : com.example.personalassistant.feature.profile.data.CurrentAccountRepository {
            private val state = MutableStateFlow(1L)
            override suspend fun currentAccountId(): Long = state.value
            override fun currentAccountIdFlow(): Flow<Long> = state
            override suspend fun setCurrentAccountId(accountId: Long) {
                throw IllegalStateException("磁盘写入失败")
            }
        }
        val vm = AccountSwitcherViewModel(
            accountRepository = FakeAccountRepository(listOf(accountA, accountB)),
            currentAccountRepository = failing,
        )
        advanceUntilIdle()
        vm.switchTo(2L)
        advanceUntilIdle()
        assertNotNull(vm.state.value.errorMessage)

        vm.consumeError()

        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `账户清单变化后重算 visible`() = runTest(testDispatcher) {
        // 从「新建第二个账户」到「两个账户可切换」这条链路：
        // 账户数变化必须让 isVisible 跟着变，否则新建完账户切换条不会出现。
        val (vm, _) = buildVm(accounts = listOf(accountA))
        advanceUntilIdle()
        assertFalse(vm.state.value.isVisible)
    }
}