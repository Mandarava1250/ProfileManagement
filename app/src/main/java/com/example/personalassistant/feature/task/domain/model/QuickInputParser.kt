package com.example.personalassistant.feature.task.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Phase 18：自然语言日期解析
 *
 * 滴答的核心交互之一：在标题里直接写「明天」「下周一」「3 天后」「周五」，
 * 提交时自动拆成**标题 + 截止日期**，用户不用跳到别的界面去设日期。
 *
 * ## 支持的写法
 *
 * | 写法 | 含义 |
 * |------|------|
 * | `今天` / `今日` | 今天 |
 * | `明天` / `明日` | 明天 |
 * | `后天` | 后天 |
 * | `大后天` | 大后天 |
 * | `昨天` / `昨日` | 昨天（补录过去的事） |
 * | `N 天后` / `N天后` | 今天 + N 天 |
 * | `N 天前` | 今天 - N 天 |
 * | `下周一` … `下周日` / `下礼拜一` … | 下周的那一天 |
 * | `本周一` … `本周日` / `这周一` … | 本周的那一天（今天之后） |
 * | `周一` … `周日` / `星期一` … | 最近的那一天（含今天；今天过了就顺延一周） |
 * | `X月Y日` / `X月Y号` | 今年的那一天（已过则顺延到明年） |
 * | `YYYY-MM-DD` / `YYYY/MM/DD` | 绝对日期 |
 *
 * ## 设计约束
 *
 * - **纯函数**：不碰系统时钟，「今天」由调用方传入 —— 单测可确定地覆盖。
 * - **保守解析**：只在能明确判定时才拆日期。拿不准就**原样当标题**，
 *   绝不猜 —— 猜错了会把用户的内容改坏。
 * - **只拆末尾**：日期短语必须在标题末尾（前面可以有别的字）。
 */
data class ParsedQuickInput(
    /** 拆完日期后的标题（可能为空 —— 比如整句就是「明天」） */
    val title: String,
    /** 解析出的截止日期（毫秒时间戳）；null = 没解析出日期 */
    val dueDate: Long?,
    /** 解析出的重复规则（Phase 18 暂只留字段，不落库） */
    val repeatRule: String? = null,
)

/**
 * 解析快速输入。
 *
 * @param input 用户输入的原始文本
 * @param today 「今天」（调用方按本地时区算好）
 * @param zoneId 本地时区（决定「当天几点」）
 * @param defaultTime 没写具体时刻时的默认时刻（滴答默认 9:00）
 */
fun parseQuickInput(
    input: String,
    today: LocalDate,
    zoneId: ZoneId,
    defaultTime: LocalTime = LocalTime.of(9, 0),
): ParsedQuickInput {
    val text = input.trim()
    if (text.isEmpty()) {
        return ParsedQuickInput(title = "", dueDate = null)
    }

    // 1) 先试绝对日期（2026-11-05 / 2026/11/05）—— 格式最明确，优先
    parseAbsoluteDate(text, today, zoneId, defaultTime)?.let { return it }

    // 2) 再试「末尾的日期短语」—— 从长到短，避免「3 天后」被「天后」抢走
    for (candidate in TAIL_PATTERNS) {
        val match = candidate.regex.find(text) ?: continue
        // 必须落在末尾（正则已用 $ 锚定，这里是双保险）
        if (match.range.last != text.lastIndex) continue

        val date = candidate.resolve(match, today) ?: continue
        // ⚠️ 必须取**分组 1**（前缀）的范围，不是整个匹配的范围 ——
        // 整个匹配从 0 开始，用它截前缀会把标题截成空串。
        val prefixEnd = match.groups[1]?.range?.last?.plus(1) ?: 0
        val title = text.substring(0, prefixEnd).trim()
        return ParsedQuickInput(
            title = title,
            dueDate = date.atTime(defaultTime).atZone(zoneId).toInstant().toEpochMilli(),
        )
    }

    // 3) 拆不出来 —— 原样当标题
    return ParsedQuickInput(title = text, dueDate = null)
}

/**
 * 绝对日期：`YYYY-MM-DD` / `YYYY/MM/DD`
 *
 * 正则分组：1=前缀、2=年、3=月、4=日
 */
private fun parseAbsoluteDate(
    text: String,
    today: LocalDate,
    zoneId: ZoneId,
    defaultTime: LocalTime,
): ParsedQuickInput? {
    val match = Regex("""^(.*?)\s*(\d{4})[-/](\d{1,2})[-/](\d{1,2})$""").find(text) ?: return null
    val groups = match.groupValues

    val date = runCatching {
        LocalDate.of(groups[2].toInt(), groups[3].toInt(), groups[4].toInt())
    }.getOrNull() ?: return null

    return ParsedQuickInput(
        title = groups[1].trim(),
        dueDate = date.atTime(defaultTime).atZone(zoneId).toInstant().toEpochMilli(),
    )
}

/**
 * 末尾日期短语的候选表。
 *
 * 顺序很重要：长模式在前（「3 天后」要先于「天后」匹配）。
 */
private sealed interface TailPattern {

    /** 正则（必须整体贴住末尾） */
    val regex: Regex

    /** 从匹配结果算出日期；null = 这个候选不适用 */
    fun resolve(match: MatchResult, today: LocalDate): LocalDate?

    /** 「今天 / 明天 / 后天 / 昨天」等固定词 */
    data class Fixed(override val regex: Regex, val offsetDays: Long) : TailPattern {
        override fun resolve(match: MatchResult, today: LocalDate): LocalDate =
            today.plusDays(offsetDays)
    }

    /**
     * 「N 天后 / N 天前」
     *
     * 正则分组：1=前缀、2=天数、3=方向（后 / 前）
     */
    data class RelativeDays(override val regex: Regex) : TailPattern {
        override fun resolve(match: MatchResult, today: LocalDate): LocalDate? {
            val groups = match.groupValues
            val days = groups[2].toLongOrNull() ?: return null
            if (days <= 0L) return null
            val sign = if (groups[3].startsWith("后")) 1L else -1L
            return today.plusDays(sign * days)
        }
    }

    /**
     * 「下周一」/「本周三」/「周五」这类星期
     *
     * 正则分组：1=前缀、2=星期字符
     */
    data class Weekday(override val regex: Regex, val weekOffset: Long?) : TailPattern {
        override fun resolve(match: MatchResult, today: LocalDate): LocalDate? {
            val target = match.groupValues[2].toDayOfWeekOrNull() ?: return null
            val current = today.dayOfWeek

            if (weekOffset != null) {
                // 「下周一」= 本周一的 + 7 天；「本周一」= 本周一（今天过了就顺延一周）
                val monday = today.minusDays((current.value - 1).toLong())
                val candidate = monday
                    .plusDays(weekOffset * 7)
                    .plusDays((target.value - 1).toLong())
                return if (weekOffset == 0L && candidate.isBefore(today)) {
                    // 「本周X」若**严格早于**今天则顺延一周（等于今天不顺延 —— 今天就是本周X）
                    candidate.plusWeeks(1)
                } else {
                    candidate
                }
            }

            // 「周五」= 最近的那个周五（含今天；今天过了就顺延一周）
            var delta = (target.value - current.value).toLong()
            if (delta < 0) delta += 7
            return today.plusDays(delta)
        }
    }

    /**
     * 「X 月 Y 日」
     *
     * 正则分组：1=前缀、2=月、3=日
     */
    data class MonthDay(override val regex: Regex) : TailPattern {
        override fun resolve(match: MatchResult, today: LocalDate): LocalDate? {
            val groups = match.groupValues
            val month = groups[2].toIntOrNull() ?: return null
            val day = groups[3].toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31) return null
            val candidate = runCatching { LocalDate.of(today.year, month, day) }
                .getOrNull() ?: return null
            // 今年已过 → 顺延到明年
            return if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
        }
    }
}

private fun String.toDayOfWeekOrNull(): DayOfWeek? = when (trim()) {
    "一", "1" -> DayOfWeek.MONDAY
    "二", "2" -> DayOfWeek.TUESDAY
    "三", "3" -> DayOfWeek.WEDNESDAY
    "四", "4" -> DayOfWeek.THURSDAY
    "五", "5" -> DayOfWeek.FRIDAY
    "六", "6" -> DayOfWeek.SATURDAY
    "日", "7", "天" -> DayOfWeek.SUNDAY
    else -> null
}

/**
 * 候选表。
 *
 * ⚠️ 顺序即优先级：长的、能明确判定的排前面。
 */
private val TAIL_PATTERNS: List<TailPattern> = listOf(
    // 「N 天后 / N 天前」—— 必须排在固定词之前，否则「3天后」会被误伤
    TailPattern.RelativeDays(
        regex = Regex("""^(.*?)(\d+)\s*[天日]\s*(后|前|之后|之前)$"""),
    ),

    // 「大后天」要在「后天」前面
    TailPattern.Fixed(regex = Regex("""^(.*?)大后天$"""), offsetDays = 3L),
    TailPattern.Fixed(regex = Regex("""^(.*?)后天$"""), offsetDays = 2L),
    TailPattern.Fixed(regex = Regex("""^(.*?)(今天|今日|今晚)$"""), offsetDays = 0L),
    TailPattern.Fixed(regex = Regex("""^(.*?)(明天|明日|明晚)$"""), offsetDays = 1L),
    TailPattern.Fixed(regex = Regex("""^(.*?)(昨天|昨日|昨晚)$"""), offsetDays = -1L),

    // 「下周三 / 下礼拜三」—— 先于「本周」与裸星期
    TailPattern.Weekday(
        regex = Regex("""^(.*?)下(?:周|个?周|星期|礼拜)([一二三四五六日天1-7])$"""),
        weekOffset = 1L,
    ),
    TailPattern.Weekday(
        regex = Regex("""^(.*?)(?:本|这)(?:周|个?周|星期|礼拜)([一二三四五六日天1-7])$"""),
        weekOffset = 0L,
    ),
    // 裸星期（「周五」/「星期五」）
    TailPattern.Weekday(
        regex = Regex("""^(.*?)(?:周|星期|礼拜)([一二三四五六日天1-7])$"""),
        weekOffset = null,
    ),

    // 「11月5日」/「11月5号」
    TailPattern.MonthDay(regex = Regex("""^(.*?)(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]$""")),
)
