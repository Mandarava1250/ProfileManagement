package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Phase 18：自然语言日期解析单测
 *
 * 纯函数 + 「今天」由外部传入 → 边界可确定地覆盖。
 */
class QuickInputParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 2026-11-02 是周一 */
    private val monday: LocalDate = LocalDate.of(2026, 11, 2)

    private val defaultTime: LocalTime = LocalTime.of(9, 0)

    private fun parse(input: String, today: LocalDate = monday) =
        parseQuickInput(input, today, zone, defaultTime)

    /** 取解析出的截止日期（当天几点） */
    private fun dueDateOf(input: String, today: LocalDate = monday) =
        parse(input, today).dueDate?.let {
            java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }

    // ===== 固定词 =====

    @Test
    fun `今天解析成今天`() {
        val r = parse("开会 今天")
        assertEquals("开会", r.title)
        assertEquals(monday, dueDateOf("开会 今天"))
    }

    @Test
    fun `明天解析成明天`() {
        val r = parse("交周报 明天")
        assertEquals("交周报", r.title)
        assertEquals(monday.plusDays(1), dueDateOf("交周报 明天"))
    }

    @Test
    fun `后天与大后天`() {
        assertEquals(monday.plusDays(2), dueDateOf("x 后天"))
        assertEquals(monday.plusDays(3), dueDateOf("x 大后天"))
    }

    @Test
    fun `昨天解析成昨天`() {
        assertEquals(monday.minusDays(1), dueDateOf("补记 昨天"))
    }

    @Test
    fun `明日与今日等同明天今天`() {
        assertEquals(monday.plusDays(1), dueDateOf("x 明日"))
        assertEquals(monday, dueDateOf("x 今日"))
    }

    // ===== N 天后 / N 天前 =====

    @Test
    fun `3 天后解析成三天后`() {
        val r = parse("还书 3天后")
        assertEquals("还书", r.title)
        assertEquals(monday.plusDays(3), dueDateOf("还书 3天后"))
    }

    @Test
    fun `N 天前解析成过去`() {
        assertEquals(monday.minusDays(5), dueDateOf("x 5天前"))
    }

    @Test
    fun `0 天后不解析 —— 数字必须是正数`() {
        // 「0天后」不合理，当普通标题
        val r = parse("0天后")
        assertNull(r.dueDate)
        assertEquals("0天后", r.title)
    }

    // ===== 星期 =====

    @Test
    fun `周五解析成本周五`() {
        assertEquals(monday.plusDays(4), dueDateOf("x 周五"))
    }

    @Test
    fun `星期一就是今天`() {
        assertEquals(monday, dueDateOf("x 星期一"))
    }

    @Test
    fun `周日解析到本周日`() {
        assertEquals(monday.plusDays(6), dueDateOf("x 周日"))
    }

    @Test
    fun `下周一解析到下周一`() {
        assertEquals(monday.plusDays(7), dueDateOf("x 下周一"))
    }

    @Test
    fun `下周日解析到下周日`() {
        assertEquals(monday.plusDays(13), dueDateOf("x 下周日"))
    }

    @Test
    fun `下礼拜三与下周三等价`() {
        assertEquals(dueDateOf("x 下周三"), dueDateOf("x 下礼拜三"))
    }

    @Test
    fun `本周三解析到本周三`() {
        assertEquals(monday.plusDays(2), dueDateOf("x 本周三"))
    }

    @Test
    fun `本周一已过则顺延一周`() {
        // 今天就是周一 —— 「本周一」若含今天则不顺延
        assertEquals(monday, dueDateOf("x 本周一"))
        // 从周二看，「本周一」已过 → 顺延到下周一
        assertEquals(
            monday.plusDays(7),
            dueDateOf("x 本周一", today = monday.plusDays(1)),
        )
    }

    @Test
    fun `已经过去的裸星期顺延到下周`() {
        // 从周三（11-04）看，「周一」已过 → 下一个周一是 11-09
        assertEquals(
            monday.plusDays(7),
            dueDateOf("x 周一", today = monday.plusDays(2)),
        )
    }

    // ===== 月日 =====

    @Test
    fun `11 月 5 日解析成今年那一天`() {
        assertEquals(LocalDate.of(2026, 11, 5), dueDateOf("x 11月5日"))
    }

    @Test
    fun `11 月 5 号与 11 月 5 日等价`() {
        assertEquals(dueDateOf("x 11月5日"), dueDateOf("x 11月5号"))
    }

    @Test
    fun `今年的日期已过则顺延到明年`() {
        assertEquals(
            LocalDate.of(2027, 1, 15),
            dueDateOf("x 1月15日"),
        )
    }

    @Test
    fun `非法月日不解析`() {
        val r = parse("x 13月40日")
        assertNull(r.dueDate)
        assertEquals("x 13月40日", r.title)
    }

    // ===== 绝对日期 =====

    @Test
    fun `2026-12-01 解析成绝对日期`() {
        val r = parse("体检 2026-12-01")
        assertEquals("体检", r.title)
        assertEquals(LocalDate.of(2026, 12, 1), dueDateOf("体检 2026-12-01"))
    }

    @Test
    fun `斜杠格式也能解析`() {
        assertEquals(
            LocalDate.of(2026, 12, 1),
            dueDateOf("体检 2026/12/01"),
        )
    }

    @Test
    fun `非法绝对日期不解析`() {
        val r = parse("x 2026-13-45")
        assertNull(r.dueDate)
        assertEquals("x 2026-13-45", r.title)
    }

    // ===== 保守解析 =====

    @Test
    fun `没有日期就原样当标题`() {
        val r = parse("买牛奶")
        assertNull(r.dueDate)
        assertEquals("买牛奶", r.title)
    }

    @Test
    fun `日期短语不在末尾则不拆`() {
        // 「明天」在中间 —— 不拆，否则标题会被改坏
        val r = parse("明天买牛奶")
        assertNull(r.dueDate)
        assertEquals("明天买牛奶", r.title)
    }

    @Test
    fun `整句就是日期时标题为空`() {
        val r = parse("明天")
        assertEquals("", r.title)
        assertNotNull(r.dueDate)
    }

    @Test
    fun `空输入返回空标题`() {
        val r = parse("   ")
        assertEquals("", r.title)
        assertNull(r.dueDate)
    }

    @Test
    fun `标题里的中文数字不被误伤`() {
        // 「三天后」不是「3 天后」—— 中文数字不解析（保守原则）
        val r = parse("x 三天后")
        assertNull(r.dueDate)
    }

    @Test
    fun `默认时刻是九点`() {
        val r = parse("x 明天")
        val due = java.time.Instant.ofEpochMilli(r.dueDate!!).atZone(zone)
        assertEquals(LocalTime.of(9, 0), due.toLocalTime())
    }
}
