package com.example.personalassistant.feature.profile.presentation.accountswitcher

import com.example.personalassistant.feature.profile.data.local.AccountIds

/**
 * Phase 9b B2：账户切换条的 UI 状态。
 *
 * ## 为什么单独开一份状态，而不复用 [com.example.personalassistant.feature.finance.presentation.FinanceUiState]
 * 记账页那份 state 的 `combine` 已经占满 Kotlin 的 5 流重载上限（见 `FinanceViewModel` 注释），
 * 再塞账户清单进去必须改写成 `list + map` 的弱类型写法；而账户清单与「当前选中账户」
 * 是同一个 `CurrentAccountRepository` 的两面，单独一条 StateFlow 成本最低、也不动到既有链路。
 *
 * ## 与 `AccountSettingsCard` 的分工
 * Settings 页那张卡是**管理**入口（新建 / 重命名 / 删除 / 头像 / 期初余额）；
 * 本状态只服务**切换**这一件事——记账页顶部的高频动作，不该把管理能力也拖进来。
 */
data class AccountSwitcherState(
    /** 初始为 true：账户清单第一次回流之前，UI 不该先把「只有一个账户」当成事实画出来 */
    val isLoading: Boolean = true,
    /** 账户清单（按 id 升序，来自 `AccountRepository.observeAccounts()`） */
    val accounts: List<AccountSwitcherItem> = emptyList(),
    /** 当前选中账户 id（来自 `CurrentAccountRepository`） */
    val currentAccountId: Long = AccountIds.DEFAULT_ACCOUNT_ID,
    /** 正在执行切账户写入（期间 UI 应禁用重复点击，避免连点产生多次 DataStore 写） */
    val isSwitching: Boolean = false,
    /** 一次性错误提示（切账户失败等），消费后由 [AccountSwitcherAction.ErrorConsumed] 清空 */
    val errorMessage: String? = null,
) {
    /**
     * 是否值得渲染切换条。
     *
     * 只有一个账户时切换条没有信息量，反而挤占记账页首屏高度 —— 此时应整条隐藏。
     * 加载中同样隐藏（[isLoading] 为 true 时 [accounts] 还是空表，画出来只是闪烁）。
     */
    val isVisible: Boolean
        get() = !isLoading && accounts.size >= 2

    /** 当前账户的显示名（找不到时回落到空串，由 UI 决定怎么展示） */
    val currentAccountName: String
        get() = accounts.firstOrNull { it.id == currentAccountId }?.name.orEmpty()
}

/**
 * 切换条上的单个账户。
 *
 * 不复用 [com.example.personalassistant.feature.profile.data.local.UserProfileEntity] 的原因：
 * 那个实体带 `createdAt` / `initialBalance` 等与「切换」无关的列，
 * 直接透传会让 UI 层以为切换条关心这些字段；这里只取切换真正要用到的三项，
 * 让「切换不碰记账数据」这条边界在类型上就看得见。
 */
data class AccountSwitcherItem(
    val id: Long,
    val name: String,
    val avatarUri: String? = null,
)

/**
 * 切换条的全部意图。
 *
 * 命名分两类：
 * - `*Requested` / `SwitchRequested`：来自 UI 的用户意图
 * - `*Loaded` / `*Succeeded` / `*Failed`：来自数据层的回流
 * 这样 reducer 一眼能看出哪些是外部真相、哪些是本地乐观更新。
 */
sealed interface AccountSwitcherAction {

    /** 开始加载（清掉上一次的错误，避免旧错误在新一轮里阴魂不散） */
    object LoadStarted : AccountSwitcherAction

    /**
     * 账户清单 + 当前账户回流。
     *
     * 两者一起送进来而不是分两条 action：它们来自不同 Flow，分开更新会出现
     * 「新清单 + 旧 currentId」的中间帧，UI 上就是一帧错误的选中态。
     */
    data class AccountsLoaded(
        val accounts: List<AccountSwitcherItem>,
        val currentAccountId: Long,
    ) : AccountSwitcherAction

    /** 用户点了某个账户 Chip */
    data class SwitchRequested(val accountId: Long) : AccountSwitcherAction

    /** 切换写入成功 */
    data class SwitchSucceeded(val accountId: Long) : AccountSwitcherAction

    /** 切换写入失败（DataStore IO 异常等） */
    data class SwitchFailed(val message: String) : AccountSwitcherAction

    /** UI 已展示过错误，清空 */
    object ErrorConsumed : AccountSwitcherAction
}

/**
 * 纯函数 reducer：`(state, action) -> state`。
 *
 * 抽成顶层函数（而不是 VM 私有方法）是为了能在 JVM 测试里直接喂 action 断言状态迁移，
 * 不必构造 VM、不必起协程调度器 —— 状态机本身是同步的，测试也该是同步的。
 */
internal fun reduceAccountSwitcher(
    state: AccountSwitcherState,
    action: AccountSwitcherAction,
): AccountSwitcherState = when (action) {
    AccountSwitcherAction.LoadStarted ->
        state.copy(isLoading = true, errorMessage = null)

    is AccountSwitcherAction.AccountsLoaded -> {
        // 悬空 id 兜底：账户可能被 Settings 页删掉，而 DataStore 里的 currentId 还指着它。
        // 实现类虽然会在删除时顺手改偏好，但那是「实现类的自觉」；
        // 切换条作为读方，自己也得能扛住一个指向不存在账户的 id。
        val ids = action.accounts.mapTo(mutableSetOf()) { it.id }
        val resolved = if (action.currentAccountId in ids) {
            action.currentAccountId
        } else {
            action.accounts.firstOrNull()?.id ?: action.currentAccountId
        }
        state.copy(
            isLoading = false,
            accounts = action.accounts,
            currentAccountId = resolved,
            // 清单回流同时收敛 isSwitching：若写入成功但 Flow 回流慢于回调，
            // 这里兜住「一直在转圈」的状态
            isSwitching = false,
            errorMessage = null,
        )
    }

    is AccountSwitcherAction.SwitchRequested ->
        // 点当前账户 = no-op：既不该闪一下 loading，也不该产生一次无意义的 DataStore 写
        if (action.accountId == state.currentAccountId) {
            state
        } else {
            state.copy(isSwitching = true, errorMessage = null)
        }

    is AccountSwitcherAction.SwitchSucceeded ->
        state.copy(
            isSwitching = false,
            // 乐观更新：真正的 currentId 由 currentAccountIdFlow 回流确认，
            // 这里先改是为了让 Chip 选中态立刻跟上手指，不等一次 DataStore 往返
            currentAccountId = action.accountId,
            errorMessage = null,
        )

    is AccountSwitcherAction.SwitchFailed ->
        state.copy(isSwitching = false, errorMessage = action.message)

    AccountSwitcherAction.ErrorConsumed ->
        state.copy(errorMessage = null)
}