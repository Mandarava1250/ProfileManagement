package com.example.personalassistant.feature.finance.domain.repository

import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode
import kotlinx.coroutines.flow.Flow

/**
 * 批次 C：finance 模块的显示偏好读写入口
 *
 * ## 为什么抽成接口
 *
 * 跟 `com.example.personalassistant.feature.settings.FinanceCutoffSettings` 同理：
 * 接口在 domain、实现是 DataStore 的具体类，单测里用内存 fake，零 IO、行为确定。
 * 直接依赖 DataStore 的具体类会让 ViewModel 单测挂在真实 IO 上，`runTest` 的虚拟时间推不动，
 * 测试只能靠 sleep 猜时机（必然 flaky）。
 *
 * ## 实现
 *
 * DataStore（见 `com.example.personalassistant.feature.finance.data.preferences.FinanceDisplayRepositoryImpl`），
 * **不新增 Room 表** —— 这是显示偏好，不是业务数据。
 */
interface FinanceDisplayRepository {

    /** 观察「今日剩余」口径（默认值见 [TodayRemainingMode.DEFAULT]）。 */
    fun observeTodayRemainingMode(): Flow<TodayRemainingMode>

    /** 写入「今日剩余」口径。 */
    suspend fun setTodayRemainingMode(mode: TodayRemainingMode)
}
