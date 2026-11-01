package com.example.personalassistant.core.holiday.usecase

import com.example.personalassistant.core.holiday.data.HolidayRepository
import javax.inject.Inject

/**
 * Phase 8 Sub-step 7：触发节假日数据同步。
 *
 * 触发场景：
 * 1. 用户在 Settings 卡片点击「同步节假日数据」
 * 2. 应用启动时检测到「当前年份 > 本地数据最大年份」时弹出授权,用户同意后后
 *
 * Phase 8 Sub-step 7 默认通过 [com.example.personalassistant.core.holiday.data.StubHolidaySyncGateway]
 * 实现，仅返回 success，无网络请求。生产环境需替换为真 HTTP 客户端。
 *
 * @return true = 同步成功；false = 同步失败
 */
class TriggerHolidaySyncUseCase @Inject constructor(
    private val holidayRepository: HolidayRepository,
) {
    suspend operator fun invoke(year: Int): Boolean =
        holidayRepository.syncHolidaysForYear(year)
}