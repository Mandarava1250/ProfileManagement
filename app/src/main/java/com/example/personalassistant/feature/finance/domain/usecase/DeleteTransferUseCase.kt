package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 5：删除一笔转账（成对删两条流水）。
 *
 * ## 为什么不复用 [DeleteTransactionUseCase]
 * 那个走的是「单行删除 + 回滚零钱池」，用在转账上会有两个后果：
 * 1. Repository 的 `deleteTransaction` 遇到转账行直接抛
 *    [com.example.personalassistant.feature.finance.domain.repository.TransferNotAllowedException]
 *    —— 调用方若用 runCatching 且不接 onFailure，就变成**点了没反应的静默失败**；
 * 2. 假设它不拦，只删一半会留下**半对转账**：钱从一个账户出去了却没进另一个，
 *    而余额是派生值，两边会同时显示错数字，且没有任何一行数据能提示这里断过。
 *
 * 所以转账单独一条路，和 UI 侧的分流（AddTransactionViewModel.deleteCurrent）配对。
 *
 * @return 实际删除的行数（正常为 2；0 表示没找到，调用方应提示用户而不是当成功）
 */
class DeleteTransferUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(id: Long): Int = repository.deleteTransfer(id)
}
