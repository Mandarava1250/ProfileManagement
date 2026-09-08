package com.example.personalassistant.core.holiday.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 7：Stub 同步网关
 *
 * 当前 Phase 仅返回 success，不发起任何网络请求。
 * 原因：Sub-step 7 边界确认时已说明「网络同步仅占位框架」。
 *
 * 未来实现路径：
 * 1. 选定数据源（如 Calendarific API、国务院放假通知 JSON）
 * 2. 在 OkHttp + Retrofit 模块加 HTTP 客户端
 * 3. 解析响应 → 转换成 [com.example.personalassistant.core.holiday.model.HolidayEntity]
 * 4. 用 Hilt @Binds 把 [HolidaySyncGateway] 绑定到真实现，替换本 Stub
 *
 * [sync] 始终返回 true（数据已最新），避免触发「重新授权」弹窗。
 */
@Singleton
class StubHolidaySyncGateway @Inject constructor() : HolidaySyncGateway {
    override suspend fun sync(year: Int): Boolean {
        // 占位：什么都不做，宣称成功
        return true
    }
}