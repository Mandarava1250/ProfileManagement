package com.example.personalassistant.core.holiday.usecase

import com.example.personalassistant.core.holiday.data.HolidayRepository
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.core.holiday.model.HolidayStrategy
import com.example.personalassistant.core.holiday.model.HolidayType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 8 Sub-step 7：节假日策略应用 UseCase 测试
 *
 * 不接 Room,用 FakeHolidayRepository(内存 Map)覆盖 4 种策略。
 */
class ApplyHolidayStrategyUseCaseTest {

    private fun fakeRepo(vararg holidays: HolidayEntity) = object : HolidayRepository {
        private val map = holidays.associateBy { LocalDate.ofEpochDay(it.dateEpochDay) to it.type }

        override suspend fun getHolidaysByDate(date: LocalDate): List<HolidayEntity> =
            map.filterKeys { it.first == date }.values.toList()

        override suspend fun findHoliday(date: LocalDate): HolidayEntity? =
            map[date to HolidayType.HOLIDAY]

        override suspend fun findWorkdayOverride(date: LocalDate): HolidayEntity? =
            map[date to HolidayType.WORKDAY]

        override suspend fun getHolidaysInRange(from: LocalDate, to: LocalDate): List<HolidayEntity> =
            map.filterKeys { it.first in from..to }.values.toList()

        override fun observeHolidaysInRange(from: LocalDate, to: LocalDate): Flow<List<HolidayEntity>> =
            flowOf(emptyList())

        override suspend fun getMaxYear(): Int? = 2026
        override suspend fun getMinYear(): Int? = 2025
        override suspend fun count(): Int = map.size
        override suspend fun syncHolidaysForYear(year: Int): Boolean = true
        override suspend fun clearAll() {}
    }

    @Test
    fun skip_returns_null_when_hitting_holiday() = runTest {
        val repo = fakeRepo(
            HolidayEntity(0, LocalDate.of(2025, 10, 1).toEpochDay(), "国庆节", HolidayType.HOLIDAY, 2025),
        )
        val useCase = ApplyHolidayStrategyUseCase(repo)
        assertNull(useCase(LocalDate.of(2025, 10, 1), HolidayStrategy.SKIP))
    }

    @Test
    fun skip_returns_same_date_when_not_holiday() = runTest {
        val repo = fakeRepo()
        val useCase = ApplyHolidayStrategyUseCase(repo)
        val date = LocalDate.of(2025, 10, 9)
        assertEquals(date, useCase(date, HolidayStrategy.SKIP))
    }

    @Test
    fun advance_moves_to_previous_workday() = runTest {
        // 模拟 9/29, 9/30 也是 HOLIDAY; 9/28 是 WORKDAY(调休补班),UseCase 不把它当避让
        val repo = fakeRepo(
            HolidayEntity(0, LocalDate.of(2025, 9, 29).toEpochDay(), "国庆前调休", HolidayType.HOLIDAY, 2025),
            HolidayEntity(0, LocalDate.of(2025, 9, 30).toEpochDay(), "国庆前调休", HolidayType.HOLIDAY, 2025),
            HolidayEntity(0, LocalDate.of(2025, 10, 1).toEpochDay(), "国庆节", HolidayType.HOLIDAY, 2025),
        )
        val useCase = ApplyHolidayStrategyUseCase(repo)
        val result = useCase(LocalDate.of(2025, 10, 1), HolidayStrategy.ADVANCE)
        // 回溯：10/1 (HOLIDAY) → 9/30 (HOLIDAY) → 9/29 (HOLIDAY) → 9/28 (非 HOLIDAY)
        assertEquals(LocalDate.of(2025, 9, 28), result)
    }

    @Test
    fun postpone_moves_to_next_workday() = runTest {
        val repo = fakeRepo(
            HolidayEntity(0, LocalDate.of(2025, 10, 1).toEpochDay(), "国庆节", HolidayType.HOLIDAY, 2025),
        )
        val useCase = ApplyHolidayStrategyUseCase(repo)
        val result = useCase(LocalDate.of(2025, 10, 1), HolidayStrategy.POSTPONE)
        // 没有其他 HOLIDAY 标记在 fake repo,10/2 不是 HOLIDAY → 直接返回
        assertEquals(LocalDate.of(2025, 10, 2), result)
    }

    @Test
    fun keep_normal_returns_same_date_regardless_of_holiday() = runTest {
        val repo = fakeRepo(
            HolidayEntity(0, LocalDate.of(2025, 10, 1).toEpochDay(), "国庆节", HolidayType.HOLIDAY, 2025),
        )
        val useCase = ApplyHolidayStrategyUseCase(repo)
        val date = LocalDate.of(2025, 10, 1)
        assertEquals(date, useCase(date, HolidayStrategy.KEEP_NORMAL))
    }

    @Test
    fun workday_override_does_not_trigger_holiday_logic() = runTest {
        val repo = fakeRepo(
            HolidayEntity(0, LocalDate.of(2025, 9, 28).toEpochDay(), "国庆前调休", HolidayType.WORKDAY, 2025),
        )
        val useCase = ApplyHolidayStrategyUseCase(repo)
        val date = LocalDate.of(2025, 9, 28)
        assertEquals(date, useCase(date, HolidayStrategy.SKIP))
    }
}