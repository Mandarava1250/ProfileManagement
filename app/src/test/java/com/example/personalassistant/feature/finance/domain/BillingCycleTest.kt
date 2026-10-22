package com.example.personalassistant.feature.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * [BillingCycle] / [MonthCutoffPolicy] 单元测试
 *
 * ## 三组用例
 *
 * 1. **回归基线**：截止日 = 1 号时，行为与本功能上线前**逐字节相等**
 * 2. **连续性**：任何一天都恰好属于一个账期（不漏、不重）—— 这是"账对得上"的地基
 * 3. **边界**：2 月、越界夹取、生效点前后的切换
 */
class BillingCycleTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 默认策略 = 没改过 = 1 号 = 自然月 */
    private val defaultPolicy = MonthCutoffPolicy()

    /** 截止日 15，从 2026-10 起生效 */
    private val policy15 = MonthCutoffPolicy(cutoffDay = 15, effectiveFromYm = "2026-10")

    // ================= 1. 回归基线：截止日 = 1 号 =================

    @Test
    fun `cutoff one range equals legacy natural month range`() {
        listOf("2026-01", "2026-02", "2026-09", "2026-10", "2027-02").forEach { ym ->
            val legacyStart = YearMonth.parse(ym).atDay(1)
                .atStartOfDay(zone).toInstant().toEpochMilli()
            val legacyEnd = YearMonth.parse(ym).plusMonths(1).atDay(1)
                .atStartOfDay(zone).toInstant().toEpochMilli()

            val (start, end) = BillingCycle.rangeOf(ym, defaultPolicy, zone)

            assertEquals("起始必须一致：$ym", legacyStart, start)
            assertEquals("结束必须一致：$ym", legacyEnd, end)
        }
    }

    @Test
    fun `cutoff one yearMonthOf equals legacy natural month`() {
        listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 9, 15),
            LocalDate.of(2026, 10, 21),
            LocalDate.of(2026, 12, 31),
        ).forEach { date ->
            val legacy = YearMonth.from(date).toString()
            assertEquals(
                "归属必须一致：$date",
                legacy,
                BillingCycle.yearMonthOf(date, defaultPolicy, zone),
            )
        }
    }

    @Test
    fun `cutoff one remaining days equals legacy formula`() {
        listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 9, 15),
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 12, 31),
        ).forEach { date ->
            val legacy = (YearMonth.from(date).lengthOfMonth() - date.dayOfMonth + 1)
                .coerceAtLeast(1)
            assertEquals(
                "剩余天数必须一致：$date",
                legacy,
                BillingCycle.remainingDays(date, defaultPolicy, zone),
            )
        }
    }

    @Test
    fun `cutoff one display range equals natural month`() {
        assertEquals("2026-10-01 ~ 2026-10-31", BillingCycle.displayRange("2026-10", defaultPolicy))
        assertEquals("2026-02-01 ~ 2026-02-28", BillingCycle.displayRange("2026-02", defaultPolicy))
    }

    // ================= 2. 连续性：账必须对得上 =================

    @Test
    fun `consecutive cycles share exact boundary`() {
        // 上一段的 endExclusive 必须 == 下一段的 start
        listOf("2026-07", "2026-08", "2026-09", "2026-10", "2026-11").forEach { ym ->
            val (_, end) = BillingCycle.rangeOf(ym, policy15, zone)
            val nextYm = YearMonth.parse(ym).plusMonths(1).toString()
            val (nextStart, _) = BillingCycle.rangeOf(nextYm, policy15, zone)
            assertEquals("边界必须咬合：$ym → $nextYm", end, nextStart)
        }
    }

    @Test
    fun `every day belongs to exactly one cycle`() {
        // 跨越"生效点"（2026-10）前后连续扫 120 天，每天都必须能唯一归到一个账期，
        // 且该账期的区间确实包含这一天
        var date = LocalDate.of(2026, 6, 1)
        repeat(120) {
            val ym = BillingCycle.yearMonthOf(date, policy15, zone)
            val (start, end) = BillingCycle.rangeOf(ym, policy15, zone)
            val ts = date.atStartOfDay(zone).toInstant().toEpochMilli()
            assertTrue("$date 应落在 $ym 内", ts in start until end)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `cycles cover timeline without gap`() {
        // 把 2026-09 段和 2026-10 段拼起来，必须正好覆盖 09-01 ~ 11-14，无缝无叠
        val (sepStart, sepEnd) = BillingCycle.rangeOf("2026-09", policy15, zone)
        val (octStart, octEnd) = BillingCycle.rangeOf("2026-10", policy15, zone)
        assertEquals(sepEnd, octStart)
        assertEquals(
            LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            sepStart,
        )
        assertEquals(
            LocalDate.of(2026, 11, 15).atStartOfDay(zone).toInstant().toEpochMilli(),
            octEnd,
        )
    }

    // ================= 3. 截止日 = 15 号 =================

    @Test
    fun `cutoff fifteen segment covers fifteenth to fourteenth of next month`() {
        val (start, end) = BillingCycle.rangeOf("2026-10", policy15, zone)

        assertEquals(
            LocalDate.of(2026, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli(),
            start,
        )
        assertEquals(
            LocalDate.of(2026, 11, 15).atStartOfDay(zone).toInstant().toEpochMilli(),
            end,
        )
    }

    @Test
    fun `cutoff fifteen assigns days before fifteenth to previous cycle`() {
        // 生效点 2026-10 之后：10-15 ~ 11-14 属于 "2026-10"
        assertEquals("2026-10", BillingCycle.yearMonthOf(LocalDate.of(2026, 10, 15), policy15, zone))
        assertEquals("2026-10", BillingCycle.yearMonthOf(LocalDate.of(2026, 11, 14), policy15, zone))
        assertEquals("2026-11", BillingCycle.yearMonthOf(LocalDate.of(2026, 11, 15), policy15, zone))
        // 10-01 ~ 10-14 属于被拉长的 "2026-09"（终点从 10-01 推到 10-14）
        assertEquals("2026-09", BillingCycle.yearMonthOf(LocalDate.of(2026, 10, 1), policy15, zone))
        assertEquals("2026-09", BillingCycle.yearMonthOf(LocalDate.of(2026, 10, 14), policy15, zone))
    }

    @Test
    fun `history before effective month keeps natural month boundaries`() {
        // 生效点之前的段：起点终点都不动
        val (start, end) = BillingCycle.rangeOf("2026-08", policy15, zone)
        assertEquals(
            LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            start,
        )
        assertEquals(
            LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            end,
        )
        // 归属也还是自然月
        assertEquals(
            "2026-08",
            BillingCycle.yearMonthOf(LocalDate.of(2026, 8, 20), policy15, zone),
        )
    }

    @Test
    fun `cutoff fifteen remaining days counts to end of cycle`() {
        // 段 10-15 ~ 11-14 共 31 天；10-20 是第 6 天 → 还剩 26 天
        assertEquals(26, BillingCycle.remainingDays(LocalDate.of(2026, 10, 20), policy15, zone))
        // 段首当天：还剩整段 31 天
        assertEquals(31, BillingCycle.remainingDays(LocalDate.of(2026, 10, 15), policy15, zone))
        // 段末当天：还剩 1 天
        assertEquals(1, BillingCycle.remainingDays(LocalDate.of(2026, 11, 14), policy15, zone))
    }

    @Test
    fun `cutoff fifteen display range is closed interval`() {
        assertEquals("2026-10-15 ~ 2026-11-14", BillingCycle.displayRange("2026-10", policy15))
        // 紧邻生效点之前的那段被拉长
        assertEquals("2026-09-01 ~ 2026-10-14", BillingCycle.displayRange("2026-09", policy15))
    }

    // ================= 4. 边界 =================

    @Test
    fun `cutoff 28 works in february without overflow`() {
        val feb = MonthCutoffPolicy(cutoffDay = 28, effectiveFromYm = "2026-01")
        val (start, end) = BillingCycle.rangeOf("2026-02", feb, zone)
        assertEquals(
            LocalDate.of(2026, 2, 28).atStartOfDay(zone).toInstant().toEpochMilli(),
            start,
        )
        assertEquals(
            LocalDate.of(2026, 3, 28).atStartOfDay(zone).toInstant().toEpochMilli(),
            end,
        )
        assertEquals("2026-02-28 ~ 2026-03-27", BillingCycle.displayRange("2026-02", feb))
    }

    @Test
    fun `clamp keeps cutoff inside legal range`() {
        assertEquals(1, BillingCycle.clampCutoff(0))
        assertEquals(1, BillingCycle.clampCutoff(-5))
        assertEquals(15, BillingCycle.clampCutoff(15))
        assertEquals(28, BillingCycle.clampCutoff(28))
        assertEquals(28, BillingCycle.clampCutoff(29))
        assertEquals(28, BillingCycle.clampCutoff(31))
    }

    @Test
    fun `out of range cutoff is clamped instead of throwing`() {
        // 29 号在 2 月不存在 —— 必须被夹到 28，而不是抛 DateTimeException
        val policy = MonthCutoffPolicy(cutoffDay = 29, effectiveFromYm = "2026-01")
        val (start, _) = BillingCycle.rangeOf("2026-02", policy, zone)
        assertEquals(
            LocalDate.of(2026, 2, 28).atStartOfDay(zone).toInstant().toEpochMilli(),
            start,
        )
    }

    // ================= 5. MonthCutoffPolicy =================

    @Test
    fun `policy uses new cutoff from effective month onward`() {
        assertEquals(1, policy15.cutoffFor("2026-09"))
        assertEquals(1, policy15.cutoffFor("2026-08"))
        assertEquals(15, policy15.cutoffFor("2026-10"))
        assertEquals(15, policy15.cutoffFor("2026-11"))
        assertEquals(15, policy15.cutoffFor("2027-01"))
    }

    @Test
    fun `default policy always uses day one`() {
        assertEquals(1, defaultPolicy.cutoffFor("2020-01"))
        assertEquals(1, defaultPolicy.cutoffFor("2026-10"))
        assertEquals(1, defaultPolicy.cutoffFor("2030-12"))
        assertTrue(defaultPolicy.isDefault)
    }

    @Test
    fun `effective from is the current cycle itself`() {
        assertEquals("2026-10", MonthCutoffPolicy.effectiveFromFor("2026-10"))
        assertEquals("2026-01", MonthCutoffPolicy.effectiveFromFor("2026-01"))
    }
}
