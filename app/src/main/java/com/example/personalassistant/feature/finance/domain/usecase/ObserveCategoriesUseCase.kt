package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7a：观察分类列表 UseCase
 *
 * - 默认按全部（支出 + 收入）观察
 * - 可选 [type] 过滤只看支出分类或收入分类
 */
class ObserveCategoriesUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    fun all(): kotlinx.coroutines.flow.Flow<List<Category>> = repository.observeCategories()
    fun byType(type: TransactionType): kotlinx.coroutines.flow.Flow<List<Category>> =
        repository.observeCategoriesByType(type)
}
