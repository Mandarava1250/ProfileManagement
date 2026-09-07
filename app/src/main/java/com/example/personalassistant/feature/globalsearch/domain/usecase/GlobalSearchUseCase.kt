package com.example.personalassistant.feature.globalsearch.domain.usecase

import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchResult
import com.example.personalassistant.feature.globalsearch.domain.repository.GlobalSearchRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Phase 8 Sub-step 3：全局搜索 UseCase
 *
 * 包装 [GlobalSearchRepository.search]，是 ViewModel 与 Repository 之间的薄层。
 *
 * - 关键字校验在 ViewModel 层完成（避免空白查询触发 IO）
 * - 本类只做"无脑转发"，方便日后插入埋点 / 拦截器 / 排序策略
 */
class GlobalSearchUseCase @Inject constructor(
    private val repository: GlobalSearchRepository,
) {
    operator fun invoke(query: String): Flow<GlobalSearchResult> = repository.search(query)
}
