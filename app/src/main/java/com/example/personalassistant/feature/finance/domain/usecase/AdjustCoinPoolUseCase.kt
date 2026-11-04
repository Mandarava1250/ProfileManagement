package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * 批次 C：零钱池存 / 取
 *
 * @param delta 变动额（正 = 存入，负 = 取出）
 * @param note 备注（可空）
 * @return 操作后的余额
 *
 * ## 为什么余额允许为负、这里也不拦
 *
 * 「取 100 但只有 50」→ 余额变成 -50，这是**产品决定**：
 * 用户可能就是想记一笔「我欠自己/欠别人 50」。
 * 拦在 UseCase 层会把这个场景堵死，而且拦不住「直接设余额为负」的路径 ——
 * 要拦就得住数据层拦，而那会让用户没法记账。
 */
class AdjustCoinPoolUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(delta: Double, note: String? = null): Double =
        repository.adjustCoinPool(delta = delta, note = note)
}
