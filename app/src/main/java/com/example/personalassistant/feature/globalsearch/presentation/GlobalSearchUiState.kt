package com.example.personalassistant.feature.globalsearch.presentation

import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchHit
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchResult

/**
 * Phase 8 Sub-step 3：全局搜索 UI 状态
 *
 * 涟宝解读：
 * - [query] 是当前输入框文本，与 [result.query] **不完全相同**
 *   当 query 全是空白时，result 是空壳；非空白时两者一致
 * - [isLoading] 在初次进入或查询切换时短暂为 true（内部用 Flow 切换表示）
 */
data class GlobalSearchUiState(
    val query: String = "",
    val result: GlobalSearchResult = GlobalSearchResult(query = ""),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val hasResults: Boolean get() = result.totalCount > 0

    /**
     * UI 渲染用扁平化结果（按时间倒序 + 类型分组）
     */
    fun flattened(): List<GlobalSearchHit> = result.flattened()
}
