package com.example.personalassistant.core.holiday.data

/**
 * Phase 8 Sub-step 7：节假日数据同步网关（接口）
 *
 * 设计动机：避免 [HolidayRepositoryImpl] 直接依赖具体网络实现，
 * 方便替换为本地 JSON / HTTP API / 第三方服务。
 *
 * Phase 8 Sub-step 7 默认实现 [StubHolidaySyncGateway]，仅返回 success，
 * 不发起任何网络请求。生产环境后续接 Calendarific / 中国国务院放假通知 API。
 */
interface HolidaySyncGateway {
    /**
     * 同步指定年份的节假日数据。
     *
     * @param year 年份（如 2027）
     * @return true = 同步成功（可能只是「数据已最新」无新数据）；false = 同步失败
     */
    suspend fun sync(year: Int): Boolean
}