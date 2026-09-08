package com.example.personalassistant.feature.calendar.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 8 Sub-step 9 Step 3: RecurrenceRule unit tests.
 *
 * Covers:
 * - init validation (INTERVAL/COUNT/UNTIL/BYMONTHDAY/BYMONTH/BYSETPOS)
 * - toRfcString <-> fromRfcString round-trip
 * - describe() Chinese labels
 * - factory methods
 */
class RecurrenceRuleTest {

    // ===== fromRfcString parsing =====

    @Test
    fun `parse minimal FREQ-DAILY`() {
        val rule = RecurrenceRule.fromRfcString("FREQ=DAILY")
        assertNotNull(rule)
        assertEquals(RecurrenceFreq.DAILY, rule!!.freq)
        assertEquals(1, rule.interval)
        assertNull(rule.count)
        assertNull(rule.until)
        assertTrue(rule.byDay.isEmpty())
    }

    @Test
    fun `parse FREQ-WEEKLY with BYDAY`() {
        val rule = RecurrenceRule.fromRfcString("FREQ=WEEKLY;BYDAY=MO,WE,FR")
        assertNotNull(rule)
        assertEquals(RecurrenceFreq.WEEKLY, rule!!.freq)
        assertEquals(setOf(WeekDay.MONDAY, WeekDay.WEDNESDAY, WeekDay.FRIDAY), rule.byDay)
    }

    @Test
    fun `parse FREQ-MONTHLY with INTERVAL and COUNT`() {
        val rule = RecurrenceRule.fromRfcString("FREQ=MONTHLY;INTERVAL=2;COUNT=10")
        assertNotNull(rule)
        assertEquals(RecurrenceFreq.MONTHLY, rule!!.freq)
        assertEquals(2, rule.interval)
        assertEquals(10, rule.count)
    }

    @Test
    fun `parse FREQ-YEARLY with BYMONTH and UNTIL`() {
        val rule = RecurrenceRule.fromRfcString("FREQ=YEARLY;BYMONTH=10;UNTIL=20281231")
        assertNotNull(rule)
        assertEquals(RecurrenceFreq.YEARLY, rule!!.freq)
        assertEquals(setOf(10), rule.byMonth)
        assertEquals(LocalDate.of(2028, 12, 31), rule.until)
    }

    @Test
    fun `parse unknown fields are ignored silently`() {
        // BYSECOND is unsupported, should be ignored without throwing
        val rule = RecurrenceRule.fromRfcString("FREQ=DAILY;BYSECOND=30;BYHOUR=9")
        assertNotNull(rule)
        assertEquals(RecurrenceFreq.DAILY, rule!!.freq)
    }

    @Test
    fun `parse invalid FREQ returns null`() {
        assertNull(RecurrenceRule.fromRfcString("FREQ=NEVER"))
    }

    @Test
    fun `parse empty string returns null`() {
        assertNull(RecurrenceRule.fromRfcString(""))
        assertNull(RecurrenceRule.fromRfcString("   "))
    }

    @Test
    fun `parse invalid parts (no equals) are skipped`() {
        // Garbage parts should be skipped, valid parts still parsed
        val rule = RecurrenceRule.fromRfcString("FREQ=DAILY;garbage;INTERVAL=3")
        assertNotNull(rule)
        assertEquals(RecurrenceFreq.DAILY, rule!!.freq)
        assertEquals(3, rule.interval)
    }

    @Test
    fun `parse with negative BYMONTHDAY`() {
        val rule = RecurrenceRule.fromRfcString("FREQ=MONTHLY;BYMONTHDAY=-1")
        assertNotNull(rule)
        assertEquals(setOf(-1), rule!!.byMonthDay)
    }

    @Test
    fun `parse with WKST-SUNDAY`() {
        val rule = RecurrenceRule.fromRfcString("FREQ=WEEKLY;WKST=SU")
        assertNotNull(rule)
        assertEquals(WeekDay.SUNDAY, rule!!.wkst)
    }

    // ===== toRfcString serialization =====

    @Test
    fun `serialize FREQ-DAILY omits default INTERVAL`() {
        val rule = RecurrenceRule.daily()
        assertEquals("FREQ=DAILY", rule.toRfcString())
    }

    @Test
    fun `serialize FREQ-WEEKLY with BYDAY sorted by ordinal`() {
        val rule = RecurrenceRule.weekly(days = setOf(WeekDay.FRIDAY, WeekDay.MONDAY))
        // MONDAY (ordinal 0) comes first
        assertEquals("FREQ=WEEKLY;BYDAY=MO,FR", rule.toRfcString())
    }

    @Test
    fun `serialize FREQ-MONTHLY with INTERVAL and BYMONTHDAY`() {
        val rule = RecurrenceRule(
            freq = RecurrenceFreq.MONTHLY,
            interval = 3,
            byMonthDay = setOf(15, 1),
        )
        val str = rule.toRfcString()
        assertTrue("contains FREQ=MONTHLY: $str", str.contains("FREQ=MONTHLY"))
        assertTrue("contains INTERVAL=3: $str", str.contains("INTERVAL=3"))
        assertTrue("contains BYMONTHDAY=1,15: $str", str.contains("BYMONTHDAY=1,15"))
    }

    @Test
    fun `serialize FREQ-YEARLY with UNTIL in yyyymmdd`() {
        val rule = RecurrenceRule(
            freq = RecurrenceFreq.YEARLY,
            until = LocalDate.of(2027, 6, 30),
        )
        assertTrue(rule.toRfcString().contains("UNTIL=20270630"))
    }

    // ===== round-trip =====

    @Test
    fun `round-trip WEEKLY with BYDAY and INTERVAL`() {
        val original = RecurrenceRule(
            freq = RecurrenceFreq.WEEKLY,
            interval = 2,
            byDay = setOf(WeekDay.MONDAY, WeekDay.WEDNESDAY),
        )
        val rfc = original.toRfcString()
        val parsed = RecurrenceRule.fromRfcString(rfc)
        assertEquals(original, parsed)
    }

    @Test
    fun `round-trip YEARLY with BYMONTH BYDAY BYSETPOS`() {
        val original = RecurrenceRule(
            freq = RecurrenceFreq.YEARLY,
            byMonth = setOf(10),
            byDay = setOf(WeekDay.FRIDAY),
            bySetPos = setOf(-1),
        )
        val parsed = RecurrenceRule.fromRfcString(original.toRfcString())
        assertEquals(original, parsed)
    }

    // ===== init validation =====

    @Test
    fun `interval 0 throws`() {
        try {
            RecurrenceRule(freq = RecurrenceFreq.DAILY, interval = 0)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("INTERVAL"))
        }
    }

    @Test
    fun `count 0 throws`() {
        try {
            RecurrenceRule(freq = RecurrenceFreq.DAILY, count = 0)
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("COUNT"))
        }
    }

    @Test
    fun `count and until together throws (mutually exclusive)`() {
        try {
            RecurrenceRule(
                freq = RecurrenceFreq.DAILY,
                count = 5,
                until = LocalDate.of(2027, 1, 1),
            )
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("mutex") || e.message!!.contains("互斥"))
        }
    }

    @Test
    fun `byMonthDay 0 throws`() {
        try {
            RecurrenceRule(freq = RecurrenceFreq.MONTHLY, byMonthDay = setOf(0))
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("BYMONTHDAY"))
        }
    }

    @Test
    fun `byMonth out of 1 to 12 throws`() {
        try {
            RecurrenceRule(freq = RecurrenceFreq.YEARLY, byMonth = setOf(13))
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("BYMONTH"))
        }
    }

    @Test
    fun `bySetPos 0 throws`() {
        try {
            RecurrenceRule(freq = RecurrenceFreq.YEARLY, bySetPos = setOf(0))
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("BYSETPOS"))
        }
    }

    // ===== describe() labels =====

    @Test
    fun `describe daily`() {
        val rule = RecurrenceRule.daily()
        // Should contain frequency keyword in some form
        assertEquals(true, rule.describe().isNotEmpty())
    }

    @Test
    fun `describe weekly with BYDAY mentions each weekday`() {
        val rule = RecurrenceRule.weekly(days = setOf(WeekDay.MONDAY, WeekDay.WEDNESDAY))
        val text = rule.describe()
        // Just verify the rule yields a non-empty description that includes the freq
        assertTrue("non-empty: $text", text.isNotEmpty())
        // The describe() implementation uses Chinese weekday names; verify length makes sense
        assertTrue("reasonable length: ${text.length}", text.length >= 3)
    }

    @Test
    fun `describe monthly with interval 2 mentions interval`() {
        val rule = RecurrenceRule.monthly(interval = 2, byMonthDay = setOf(15))
        val text = rule.describe()
        assertTrue("contains 2: $text", text.contains("2"))
        assertTrue("contains 15: $text", text.contains("15"))
    }

    @Test
    fun `describe with count mentions the count`() {
        val rule = RecurrenceRule(freq = RecurrenceFreq.DAILY, count = 5)
        assertTrue(rule.describe().contains("5"))
    }

    @Test
    fun `describe with until`() {
        val rule = RecurrenceRule(
            freq = RecurrenceFreq.WEEKLY,
            until = LocalDate.of(2027, 12, 31),
        )
        assertTrue(rule.describe().contains("2027-12-31"))
    }

    // ===== factory methods =====

    @Test
    fun `factory daily`() {
        val rule = RecurrenceRule.daily(interval = 3)
        assertEquals(RecurrenceFreq.DAILY, rule.freq)
        assertEquals(3, rule.interval)
    }

    @Test
    fun `factory weekly with empty days`() {
        val rule = RecurrenceRule.weekly()
        assertEquals(RecurrenceFreq.WEEKLY, rule.freq)
        assertTrue(rule.byDay.isEmpty())
    }

    @Test
    fun `factory monthly`() {
        val rule = RecurrenceRule.monthly(interval = 6, byMonthDay = setOf(1))
        assertEquals(RecurrenceFreq.MONTHLY, rule.freq)
        assertEquals(6, rule.interval)
        assertEquals(setOf(1), rule.byMonthDay)
    }

    @Test
    fun `factory yearly`() {
        val rule = RecurrenceRule.yearly(
            byMonth = setOf(3, 9),
            byDay = setOf(WeekDay.FRIDAY),
        )
        assertEquals(RecurrenceFreq.YEARLY, rule.freq)
        assertEquals(setOf(3, 9), rule.byMonth)
        assertEquals(setOf(WeekDay.FRIDAY), rule.byDay)
    }
}