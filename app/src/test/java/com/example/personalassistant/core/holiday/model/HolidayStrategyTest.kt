package com.example.personalassistant.core.holiday.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 8 Sub-step 7：枚举类型单测
 *
 * - [HolidayType] 三种类型完整性
 * - [HolidayStrategy] 四种策略完整性 + 默认值
 */
class HolidayStrategyTest {

    @Test
    fun `HolidayType has exactly 3 values`() {
        assertEquals(3, HolidayType.values().size)
        assertEquals(
            setOf(HolidayType.HOLIDAY, HolidayType.WORKDAY, HolidayType.OBSERVANCE),
            HolidayType.values().toSet(),
        )
    }

    @Test
    fun `HolidayStrategy has exactly 4 values`() {
        assertEquals(4, HolidayStrategy.values().size)
        assertEquals(
            setOf(
                HolidayStrategy.SKIP,
                HolidayStrategy.ADVANCE,
                HolidayStrategy.POSTPONE,
                HolidayStrategy.KEEP_NORMAL,
            ),
            HolidayStrategy.values().toSet(),
        )
    }

    @Test
    fun `HolidayStrategy enum order matches plan (SKIP=default first)`() {
        // 默认值应该是 SKIP,与 TaskEntity.holidayStrategy 默认值一致
        assertEquals(HolidayStrategy.SKIP, HolidayStrategy.values()[0])
    }

    @Test
    fun `valueOf round trip`() {
        HolidayType.values().forEach { type ->
            assertEquals(type, HolidayType.valueOf(type.name))
        }
        HolidayStrategy.values().forEach { strategy ->
            assertEquals(strategy, HolidayStrategy.valueOf(strategy.name))
        }
    }
}