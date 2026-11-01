package com.example.personalassistant.feature.profile.presentation.accountswitcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.profile.data.AccountRepository
import com.example.personalassistant.feature.profile.data.CurrentAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 9b B2：记账页顶部账户切换条的 ViewModel。
 *
 * ## 职责边界（为什么不是把账户清单塞进 FinanceViewModel）
 * 账户清单与「当前选中账户」都来自 `CurrentAccountRepository` / `AccountRepository`，
 * 和记账数据（流水 / 预算 / 零钱池）不是同一批流。塞进 `FinanceViewModel` 有两个具体代价：
 * 1. 那个 `combine` 已经占满 Kotlin 的 5 流重载上限，加流必须改写成弱类型写法；
 * 2. 账户切换失败（DataStore IO 异常）属于「偏好写入」问题，混进记账页的 `feedback`
 *    通道后，「转账余额不足」和「切账户失败」会共用一条提示——排查时分不清是谁发的。
 *
 * 单独一个 VM 后，切换条的状态迁移**完全同步可测**（reducer 是纯函数），
 * 不必起记账页那一整套 UseCase。
 *
 * ## 为什么用 [MutableStateFlow] + reducer，而不直接 `combine(...).stateIn(...)`
 * `switchTo` 需要「请求中 / 成功 / 失败」三段本地状态，它们不属于任何一条上游流。
 * 用 reducer 把「上游回流」和「本地意图」收敛到同一个状态迁移函数里，
 * 就不存在「两条流各自改写同一份 state」的竞态。
 */
@HiltViewModel
class AccountSwitcherViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val currentAccountRepository: CurrentAccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountSwitcherState())
    val state: StateFlow<AccountSwitcherState> = _state.asStateFlow()

    init {
        // 先发 LoadStarted 而不是依赖初始值：初始值那行注释写的是「还不知道」，
        // 这里再显式推一次是为了让「加载开始」这条语义出现在 action 流里，
        // 将来接埋点/日志时不用猜初始态是哪个分支产生的。
        dispatch(AccountSwitcherAction.LoadStarted)

        viewModelScope.launch {
            combine(
                accountRepository.observeAccounts(),
                currentAccountRepository.currentAccountIdFlow(),
            ) { accounts, currentId ->
                // 账户清单与 currentId 合并成一个 action 再进 reducer：
                // 分两条流各自 dispatch 会出现「新清单 + 旧 id」的中间帧，
                // 那一帧在 UI 上就是一个错误的选中态。
                AccountSwitcherAction.AccountsLoaded(
                    accounts = accounts.map {
                        AccountSwitcherItem(id = it.id, name = it.name, avatarUri = it.avatarUri)
                    },
                    currentAccountId = currentId,
                )
            }.collect(::dispatch)
        }
    }

    /**
     * 切换到 [accountId]。
     *
     * 同 id 直接返回（而不是让 reducer 吃掉）：reducer 返回同一状态只是「状态没变」，
     * 但协程仍会启动、仍会往 DataStore 写一次 —— 连点当前 Chip 不该产生写事务。
     */
    fun switchTo(accountId: Long) {
        if (accountId == _state.value.currentAccountId) return

        dispatch(AccountSwitcherAction.SwitchRequested(accountId))
        viewModelScope.launch {
            runCatching { currentAccountRepository.setCurrentAccountId(accountId) }
                .onSuccess { dispatch(AccountSwitcherAction.SwitchSucceeded(accountId)) }
                .onFailure { throwable ->
                    dispatch(
                        AccountSwitcherAction.SwitchFailed(
                            throwable.message ?: "切换账户失败",
                        ),
                    )
                }
        }
    }

    /** UI 展示过错误提示后调用，避免同一条错误在重建时反复弹出 */
    fun consumeError() {
        dispatch(AccountSwitcherAction.ErrorConsumed)
    }

    private fun dispatch(action: AccountSwitcherAction) {
        _state.value = reduceAccountSwitcher(_state.value, action)
    }
}