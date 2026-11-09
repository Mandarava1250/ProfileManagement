package com.example.personalassistant.feature.task.domain.repository

import com.example.personalassistant.feature.task.domain.model.NamedFilter
import kotlinx.coroutines.flow.Flow

/**
 * Phase 19：命名过滤器仓库
 *
 * 用户保存的筛选条件组合（带名字），随时一键套用。
 *
 * 持久化走 DataStore（JSON），理由见 [NamedFilter] 的注释 ——
 * 它是用户偏好而不是业务数据，不值得为它动 Room schema。
 */
interface NamedFilterRepository {

    /** 观察全部命名过滤器（按 sortOrder 升序）。 */
    fun observe(): Flow<List<NamedFilter>>

    /**
     * 保存（新增或按名字覆盖）。
     *
     * 同名会**覆盖**旧的 —— 用户改条件后重新保存同名条目是自然操作。
     */
    suspend fun save(filter: NamedFilter)

    /** 按名字删除。 */
    suspend fun delete(name: String)

    /** 清空全部。 */
    suspend fun clear()
}
