package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.repository.CategoryInUseException
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7a：删除分类 UseCase（带引用检查）
 *
 * 返回 [Result]：
 * - Success: 删除成功
 * - Failure(CategoryInUseException): 该分类下还有交易，无法删除
 */
class DeleteCategoryUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(id: Long): Result<Unit> = runCatching {
        repository.deleteCategory(id)
    }.recoverCatching { e ->
        if (e is CategoryInUseException) throw e
        throw e
    }
}
