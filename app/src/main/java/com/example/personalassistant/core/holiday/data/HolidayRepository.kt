package com.example.personalassistant.core.holiday.data

import com.example.personalassistant.core.holiday.model.HolidayEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Phase 8 Sub-step 7：节假日仓库接口
 *
 * 把 [HolidayDao] 暴露给 domain 层（UseCase），避免 UseCase 直接依赖 Room。
 * 返回类型用 [LocalDate] 而不是 epochDay Long，让上层语义更清晰。
 */
interface HolidayRepository {

    /** 查询某天的所有节假日（含 HOLIDAY/WORKDAY/OBSERVANCE，可同日多条） */
    suspend fun getHolidaysByDate(date: LocalDate): List<HolidayEntity>

    /**
     * 查询某天的「法定假日」标记（type = HOLIDAY）。
     *
     * @return HolidayEntity 当天是 HOLIDAY，否则 null
     */
    suspend fun findHoliday(date: LocalDate): HolidayEntity?

    /**
     * 查询某天的「调休工作日」标记（type = WORKDAY）。
     *
     * @return HolidayEntity 当天是 WORKDAY，否则 null
     */
    suspend fun findWorkdayOverride(date: LocalDate): HolidayEntity?

    /** 区间查询（含端点） */
    suspend fun getHolidaysInRange(from: LocalDate, to: LocalDate): List<HolidayEntity>

    /** 区间查询（Flow 版本，用于 UI 实时刷新） */
    fun observeHolidaysInRange(from: LocalDate, to: LocalDate): Flow<List<HolidayEntity>>

    /** 当前数据中最大年份（null = 库为空） */
    suspend fun getMaxYear(): Int?

    /** 当前数据中最小年份（null = 库为空） */
    suspend fun getMinYear(): Int?

    /** 数据总条数 */
    suspend fun count(): Int

    /**
     * 通过 [HolidaySyncGateway] 同步指定年份的新数据。
     * 默认实现 [StubHolidaySyncGateway] 仅返回 success，无实际网络请求。
     *
     * @return true = 成功同步或数据已最新；false = 同步失败
     */
    suspend fun syncHolidaysForYear(year: Int): Boolean

    /** 清空所有节假日（备份恢复前使用） */
    suspend fun clearAll()
}