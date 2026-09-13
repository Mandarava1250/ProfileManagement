package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.AccountBalance
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.profile.data.AccountRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Phase 5：把「账户表 + 流水聚合」拼成可显示的余额列表。
 *
 * ## 为什么要单独一个 UseCase，而不是让 Repository 直接返回余额
 * 余额的两个组成部分**分属两张表、两个域**：
 * - 期初 `initialBalance` 在 `user_profiles`（profile 域，AccountRepository 管）
 * - 流水净额三项在 `transactions`（finance 域，FinanceRepository 管）
 *
 * 让 FinanceRepository 去返回完整余额，就得给它注入账户表 DAO —— 那会把 profile 的
 * 表结构泄漏进 finance 的数据层，而且 [FinanceRepositoryImpl] 已经依赖
 * [AccountRepository]（为转账做目标校验），再叠一层只会让职责更糊。
 * 拼装这种「读侧组合」正是 UseCase 的活。
 *
 * ## 为什么是 combine 而不是 zip
 * 两条流都来自 Room 且都是热流，任一变化都要重算（改了期初也要立刻看到新余额）。
 * zip 会要求两边同时发新值才出结果，切换账户时容易出现「一边新一边旧」的中间态。
 *
 * ## 没流水的账户也会出现在结果里
 * [FinanceRepository.observeBalanceSums] 是按 accountId 的 GROUP BY，
 * **一条流水都没有的账户不会出现在其中**。这里用账户表做外循环、缺项按 0 处理，
 * 否则新开的账户会在余额列表里凭空消失 —— 用户会以为账户没建成。
 */
class ObserveAccountBalancesUseCase @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val accountRepository: AccountRepository,
) {

    /** 全部账户的余额，按账户 id 升序（与账户管理页同一顺序） */
    operator fun invoke(): Flow<List<AccountBalance>> = combine(
        accountRepository.observeAccounts(),
        financeRepository.observeBalanceSums(),
    ) { accounts, sums ->
        val sumByAccount = sums.associateBy { it.accountId }
        accounts
            .sortedBy { it.id }
            .map { account ->
                val sum = sumByAccount[account.id]
                AccountBalance(
                    accountId = account.id,
                    accountName = account.name,
                    initialBalance = account.initialBalance,
                    income = sum?.income ?: 0.0,
                    expense = sum?.expense ?: 0.0,
                    transferNet = sum?.transferNet ?: 0.0,
                )
            }
    }
}
