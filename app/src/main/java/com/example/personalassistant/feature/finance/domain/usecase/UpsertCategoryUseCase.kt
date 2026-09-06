package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7a：新增/更新分类 UseCase
 *
 * - 校验 name 非空
 * - id=0 时新增，id>0 时更新
 */
class UpsertCategoryUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(category: Category): Long {
        require(category.name.isNotBlank()) { "分类名不能为空" }
        return repository.upsertCategory(category.copy(name = category.name.trim()))
    }
}
