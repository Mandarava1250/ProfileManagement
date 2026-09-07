package com.example.personalassistant.feature.globalsearch.domain.repository

import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchResult
import kotlinx.coroutines.flow.Flow

/**
 * Phase 8 Sub-step 3：全局搜索领域仓库接口
 *
 * 由 Data 层的 [com.example.personalassistant.feature.globalsearch.data.repository.GlobalSearchRepositoryImpl]
 * 实现。它**仅做聚合**，不直接持有 DAO —— 真正查询委托给 4 个模块各自的 Repository.searchXxx。
 *
 * ## 设计要点
 * - **空查询**：当 [query] 是空白字符串时，返回空 [GlobalSearchResult]（不做全量扫描，节省 IO）
 * - **关键字处理**：内部负责把 `query` 包成 `%...%` 后下发到各 Repository.searchXxx
 * - **响应式**：返回 Flow，任一数据源变化都会重新触发查询（4 个 Flow combine）
 * - **类型分类**：每个命中带类型 + entityId，UI 可按类型分组渲染
 */
interface GlobalSearchRepository {

    /**
     * 全局搜索
     *
     * @param query 用户输入的原始关键字（**不要预先包 `%`**）
     * @return 聚合结果 Flow（4 个数据源任意变更都会重发）
     */
    fun search(query: String): Flow<GlobalSearchResult>
}
