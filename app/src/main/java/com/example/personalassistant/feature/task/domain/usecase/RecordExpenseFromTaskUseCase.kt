package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.task.domain.model.Task
import javax.inject.Inject

/**
 * Phase 21：从清单项一键记账
 *
 * 滴答的「清单 ↔ 记账」联动：清单项填了金额，就能一键变成一笔支出，
 * 流水再指回清单项（`linkedTaskId`），两边能互相跳转。
 *
 * ## 校验
 *
 * - 清单项必须有金额 —— 没有金额就没什么可记的，**抛错**而不是记一笔 0 元
 * - 金额必须为正 —— 负数金额是数据问题，宁可报错也别写进流水表
 * - 分类由调用方指定（UI 层让用户在记账分类里选）；这里不擅自挑一个
 */
open class RecordExpenseFromTaskUseCase @Inject constructor(
    private val financeRepository: FinanceRepository,
) {
    /**
     * @param task 清单项（必须有 amount）
     * @param categoryId 记账分类 id
     * @param date 记账时间（毫秒时间戳）；null = 用清单项的截止时间，再不行用现在
     * @return 新生成的流水 id
     */
    open suspend operator fun invoke(
        task: Task,
        categoryId: Long,
        date: Long? = null,
    ): Long {
        val amount = task.amount
        require(amount != null && amount > 0.0) {
            "清单项「${task.title}」没有有效金额，不能记账"
        }
        val txDate = date ?: task.dueDate ?: System.currentTimeMillis()

        return financeRepository.addTransaction(
            type = TransactionType.EXPENSE,
            amount = amount,
            categoryId = categoryId,
            note = task.title,
            date = txDate,
            // 流水指回清单项 —— 弱引用，删任务时流水留下
            linkedTaskId = task.id,
        )
    }
}
