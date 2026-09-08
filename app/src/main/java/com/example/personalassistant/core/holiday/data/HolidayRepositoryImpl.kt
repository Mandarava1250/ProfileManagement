package com.example.personalassistant.core.holiday.data

import com.example.personalassistant.core.holiday.model.HolidayEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 7：节假日仓库实现
 *
 * 仅做 epochDay ↔ LocalDate 的转换 + 委托给 [HolidayDao]。
 * 「避让」语义由调用方在 UseCase 层组合（HOLIDAY + WORKDAY 互斥判断）。
 */
@Singleton
class HolidayRepositoryImpl @Inject constructor(
    private val holidayDao: HolidayDao,
    private val syncGateway: HolidaySyncGateway,
) : HolidayRepository {

    override suspend fun getHolidaysByDate(date: LocalDate): List<HolidayEntity> =
        holidayDao.getByDate(date.toEpochDay())

    override suspend fun findHoliday(date: LocalDate): HolidayEntity? =
        holidayDao.findByDateAndType(date.toEpochDay(), com.example.personalassistant.core.holiday.model.HolidayType.HOLIDAY)

    override suspend fun findWorkdayOverride(date: LocalDate): HolidayEntity? =
        holidayDao.findByDateAndType(date.toEpochDay(), com.example.personalassistant.core.holiday.model.HolidayType.WORKDAY)

    override suspend fun getHolidaysInRange(from: LocalDate, to: LocalDate): List<HolidayEntity> =
        holidayDao.getInRange(from.toEpochDay(), to.toEpochDay())

    override fun observeHolidaysInRange(from: LocalDate, to: LocalDate): Flow<List<HolidayEntity>> =
        holidayDao.observeInRange(from.toEpochDay(), to.toEpochDay())
            .map { list -> list.sortedBy { it.dateEpochDay } }

    override suspend fun getMaxYear(): Int? = holidayDao.getMaxYear()
    override suspend fun getMinYear(): Int? = holidayDao.getMinYear()
    override suspend fun count(): Int = holidayDao.count()

    override suspend fun syncHolidaysForYear(year: Int): Boolean = syncGateway.sync(year)

    override suspend fun clearAll() {
        holidayDao.clearAll()
    }
}