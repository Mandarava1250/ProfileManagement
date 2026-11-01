package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 5：跨账户转账 UseCase
 *
 * 薄封装，但**故意保留**这一层，不直接让 ViewModel 调 Repository：
 * 与本模块其余写操作（AddTransactionUseCase / DeleteTransactionUseCase 等）保持一致的调用形状，
 * ViewModel 统一只依赖 domain/usecase，不直接依赖 repository 的写方法。
 *
 * 校验与失败原因全部在 Repository 内完成，这里不重复判断 ——
 * 否则两处规则会漂移，而「哪一处生效」在排查时最难定位。
 *
 * 抛 [com.example.personalassistant.feature.finance.domain.repository.TransferNotAllowedException]
 * 表示业务规则不允许（金额非法 / 目标不存在 / 自转 / 余额不足 / 缺转账分类），
 * 调用方按 reason 出文案。
 *
 * @return 转出侧流水 id
 */
class TransferUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(
        toAccountId: Long,
        amount: Double,
        note: String?,
        date: Long,
        allowNegativeBalance: Boolean = false,
    ): Long = repository.transfer(
        toAccountId = toAccountId,
        amount = amount,
        note = note,
        date = date,
        allowNegativeBalance = allowNegativeBalance,
    )
}
