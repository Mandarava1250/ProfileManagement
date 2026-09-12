package com.example.personalassistant.feature.profile.data

import kotlinx.coroutines.flow.Flow

/**
 * Phase 3：当前账户偏好仓库（接口）
 *
 * ## 为什么是 DataStore 而不是 Room 表
 * 「当前选中哪个账户」是**设备级 UI 偏好**，不是业务数据：
 * - 备份/恢复时不应被覆盖（恢复备份要保留用户当前正在看的账户）
 * - 冷启动要能立刻读到，不该等 DB 打开
 * - 切账户是纯读视角切换，不该产生一次写库事务
 *
 * ## 为什么要暴露 Flow 版本
 * [com.example.personalassistant.feature.finance.data.repository.FinanceRepositoryImpl]
 * 用 `accountIdFlow.flatMapLatest { dao.query(it, ...) }` 组装读流，
 * 这样切账户后记账页 / 预算 / 零钱池 / 全局搜索会**自动重发**，不需要任何手动刷新。
 * 只有只读快照的 [currentAccountId] 是不够的（写路径用它足够，读路径必须订阅变化）。
 */
interface CurrentAccountRepository {

    /** 当前账户 id 的即时快照（写路径用） */
    suspend fun currentAccountId(): Long

    /** 当前账户 id 的可观察流（读路径用，切账户后驱动 Flow 重发） */
    fun currentAccountIdFlow(): Flow<Long>

    /** 切换当前账户；调用方负责保证 id 存在（UI 只列出真实账户） */
    suspend fun setCurrentAccountId(accountId: Long)
}
