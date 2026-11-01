package com.example.personalassistant.feature.profile.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Phase 3：测试用 Fake [CurrentAccountRepository]（项目「Fake 优先、无 Mockito」约定）
 *
 * 与 [FakeFinanceRepository] 同风格：内存态 + 可观察，不做任何真实持久化。
 *
 * 默认落在 [com.example.personalassistant.feature.profile.data.local.AccountIds.DEFAULT_ACCOUNT_ID]，
 * 与实现类的 DataStore 默认值一致，这样测试不显式设置时行为等同首次安装。
 */
class FakeCurrentAccountRepository(
    initialAccountId: Long = DEFAULT_ID,
) : CurrentAccountRepository {

    private val state = MutableStateFlow(initialAccountId)

    /** setCurrentAccountId 调用次数（断言「切账户确实写了偏好」） */
    var setCallCount: Int = 0
        private set

    /** 最近一次被设置的 id */
    var lastSetAccountId: Long? = null
        private set

    override suspend fun currentAccountId(): Long = state.first()

    override fun currentAccountIdFlow(): Flow<Long> = state

    override suspend fun setCurrentAccountId(accountId: Long) {
        require(accountId > 0L) { "accountId must be positive, got $accountId" }
        setCallCount += 1
        lastSetAccountId = accountId
        state.value = accountId
    }

    /** 测试辅助：直接改值，不计入 setCallCount（用于构造前置状态） */
    fun seedAccountId(accountId: Long) {
        state.value = accountId
    }

    fun reset() {
        state.value = DEFAULT_ID
        setCallCount = 0
        lastSetAccountId = null
    }

    companion object {
        private const val DEFAULT_ID = 1L
    }
}
