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
 * ## Phase 18.3：标签 / 清单也一起解析
 *
 * - `#Work` → 标签（`#` 必须在词首：前面是空白或开头）
 * - `~学习` → 清单（归属地，`~` 同样必须在词首）
 *
 * 解析出来的东西都带**在原始输入里的下标区间**（[QuickInputToken.range]），
 * UI 靠它把某一段从输入框里删掉（点 chip = 去掉这一项）。
 *
 * ## 设计约束
 *
 * - **纯函数**：不碰系统时钟，「今天」由调用方传入 —— 单测可确定地覆盖。
 * - **保守解析**：只在能明确判定时才拆日期。拿不准就**原样当标题**，
 *   绝不猜 —— 猜错了会把用户的内容改坏。
 * - **日期短语要在末尾**（前面可以有别的字）：这是「保守」的核心 ——
 *   「明天买牛奶」里的「明天」不是日期（它跟别的字粘在一起），
 *   但「买牛奶 明天」是。
 */
data class ParsedQuickInput(
    /** 拆完日期 / 标签 / 清单后的标题（可能为空 —— 比如整句就是「明天」） */
    val title: String,
    /** 解析出的截止日期（毫秒时间戳）；null = 没解析出日期 */
    val dueDate: Long?,
    /**
     * 原始输入（未 trim）。
     *
     * [QuickInputToken.range] 的下标是相对它算的 —— UI 要在**用户看到的文本**上
     * 删掉对应那一段，用的就是这个基准。
     */
    val sourceText: String,
    /** 解析出的重复规则（Phase 18 暂只留字段，不落库） */
    val repeatRule: String? = null,
    /** 识别出来的所有 token（日期 / 标签 / 清单），按在文本里出现的顺序。 */
    val tokens: List<QuickInputToken> = emptyList(),
) {
    /** 日期 token（最多一个 —— 日期短语只认末尾那一个）。 */
    val dateToken: QuickInputToken?
        get() = tokens.firstOrNull { it.kind == QuickInputTokenKind.DATE }

    /** 标签 token（可以有多个）。 */
    val tagTokens: List<QuickInputToken>
        get() = tokens.filter { it.kind == QuickInputTokenKind.TAG }

    /** 清单 token（只认第一个 —— 一个清单项只属于一个清单）。 */
    val listToken: QuickInputToken?
        get() = tokens.firstOrNull { it.kind == QuickInputTokenKind.LIST }
}

/**
 * Phase 18.3：快速输入里被识别出来的一段。
 *
 * @param kind 这段是什么
 * @param name 不带前缀的纯名字（标签「Work」/ 清单「学习」；日期为空串）
 * @param label chip 上显示的文案（标签「#Work」/ 清单「~学习」/ 日期「明天」）
 * @param range 这段在**原始输入**里的下标区间（闭区间，含 `#` / `~` 前缀）
 */
data class QuickInputToken(
    val kind: QuickInputTokenKind,
    val name: String,
    val label: String,
    val range: IntRange,
)

/** Phase 18.3：token 的三种类型。 */
enum class QuickInputTokenKind {
    /** 日期短语（「明天」「下周一」「2026-12-01」） */
    DATE,

    /** 标签（`#Work`） */
    TAG,

    /** 清单（`~学习`） */
    LIST,
}

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
    if (input.isBlank()) {
        return ParsedQuickInput(title = "", dueDate = null, sourceText = input)
    }

    // 解析在 trim 过的文本上做，但**下标要换算回原始输入** ——
    // chip 点掉时用户看到的是原始文本，删的必须是原始文本里那一段。
    val lead = input.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
    val trimmed = input.trim()

    // 1) 标签 / 清单（可以在文本任意位置）
    val inlineTokens = findInlineTokens(trimmed, TAG_REGEX, QuickInputTokenKind.TAG, "#") +
        findInlineTokens(trimmed, LIST_REGEX, QuickInputTokenKind.LIST, "~")

    // 2) 日期：把标签 / 清单挖掉之后再跑「末尾日期短语」算法 ——
    //    这样「买牛奶 明天 #Work」里的「明天」也能被认出来。
    val (detection, indexMap) = buildDetectionText(trimmed, inlineTokens.map { it.range })
    val dateMatch = matchTailDate(detection, today, zoneId, defaultTime)

    val dateToken = dateMatch?.let { match ->
        // detection 的下标 → trimmed 的下标
        val start = indexMap.getOrNull(match.phraseRange.first)
        val end = indexMap.getOrNull(match.phraseRange.last)
        if (start == null || end == null) {
            null
        } else {
            QuickInputToken(
                kind = QuickInputTokenKind.DATE,
                name = "",
                label = trimmed.substring(start, end + 1),
                range = start..end,
            )
        }
    }

    // 3) 汇总（此时区间都基于 trimmed）
    val trimmedTokens = listOfNotNull(dateToken) + inlineTokens
    val title = buildTitleWithout(trimmed, trimmedTokens.map { it.range })

    // 4) 区间换算回原始输入
    val tokens = trimmedTokens.map { token ->
        token.copy(range = (token.range.first + lead)..(token.range.last + lead))
    }

    return ParsedQuickInput(
        title = title,
        dueDate = dateMatch?.dueDate,
        sourceText = input,
        tokens = tokens,
    )
}

/**
 * Phase 18.3：把 [range] 这一段从 [text] 里删掉。
 *
 * 顺手吃掉紧邻的一个空格 —— 否则删完会留下两个连续空格。
 */
fun removeTokenRange(text: String, range: IntRange): String {
    if (text.isEmpty()) return text
    val start = range.first.coerceIn(0, text.length - 1)
    val end = range.last.coerceIn(start, text.length - 1)

    val sb = StringBuilder(text)
    for (i in end downTo start) sb.deleteCharAt(i)

    // 接缝处可能留下两个空格 —— 吃掉一个
    if (start > 0 && start < sb.length && sb[start - 1] == ' ' && sb[start] == ' ') {
        sb.deleteCharAt(start)
    }
    // 删的是整句开头 / 结尾时，把多出来的那个空格也去掉
    if (start == 0 && sb.isNotEmpty() && sb[0] == ' ') sb.deleteCharAt(0)
    if (end == text.length - 1 && sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
        sb.deleteCharAt(sb.length - 1)
    }
    return sb.toString()
}

/**
 * 从 [text] 里删掉 [ranges] 这些片段，剩下的是标题。
 *
 * 剩下的空白会归一（连续空格压成一个）—— 挖掉 token 会在文本里留个洞。
 */
internal fun buildTitleWithout(text: String, ranges: List<IntRange>): String {
    if (ranges.isEmpty()) return text.trim()
    val mask = BooleanArray(text.length)
    ranges.forEach { range ->
        for (i in range) if (i in text.indices) mask[i] = true
    }
    val sb = StringBuilder(text.length)
    for (i in text.indices) if (!mask[i]) sb.append(text[i])
    return sb.toString().replace(Regex("\\s+"), " ").trim()
}

/**
 * 取第 [index] 个捕获组的下标区间；没有这个组就返回 null。
 *
 * ⚠️ Kotlin 的 `MatchGroupCollection[index]` 越界时**抛异常**（不是返回 null），
 * 所以不能直接写 `groups[2]?.range` —— 不同正则的捕获组数量不一样。
 */
private fun MatchResult.groupRange(index: Int): IntRange? =
    if (groups.size > index) groups[index]?.range else null

/**
 * 找出文本里的标签 / 清单 token。
 *
 * @param regex 必须有两个分组：1 = 整段（含 `#` / `~`），2 = 不含前缀的名字
 */
private fun findInlineTokens(
    text: String,
    regex: Regex,
    kind: QuickInputTokenKind,
    prefix: String,
): List<QuickInputToken> = regex.findAll(text).mapNotNull { match ->
    val whole = match.groupRange(1) ?: return@mapNotNull null
    val body = match.groupRange(2) ?: return@mapNotNull null
    QuickInputToken(
        kind = kind,
        name = text.substring(body.first, body.last + 1),
        label = prefix + text.substring(body.first, body.last + 1),
        range = whole,
    )
}.toList()

/**
 * 把 [removed] 这些片段挖掉，得到用于**日期识别**的文本。
 *
 * 返回值第二项是下标映射：`indexMap[i]` = 检测文本第 i 个字符在 [text] 里的下标。
 * 日期短语在检测文本里找到后，靠它换算回原文。
 */
private fun buildDetectionText(text: String, removed: List<IntRange>): Pair<String, IntArray> {
    val mask = BooleanArray(text.length)
    removed.forEach { range ->
        for (i in range) if (i in text.indices) mask[i] = true
    }
    val chars = ArrayList<Char>(text.length)
    val indices = ArrayList<Int>(text.length)
    for (i in text.indices) {
        if (mask[i]) continue
        chars.add(text[i])
        indices.add(i)
    }
    // 首尾空白去掉（映射同步跟着走）
    var start = 0
    var end = chars.size
    while (start < end && chars[start].isWhitespace()) start++
    while (end > start && chars[end - 1].isWhitespace()) end--

    val detection = StringBuilder(end - start).apply {
        for (i in start until end) append(chars[i])
    }.toString()
    val indexMap = IntArray(end - start) { indices[start + it] }
    return detection to indexMap
}

/** 日期命中结果。 */
private data class DateMatch(
    /** 截止时间（毫秒时间戳）。 */
    val dueDate: Long,
    /** 日期短语在检测文本里的下标区间（闭区间）。 */
    val phraseRange: IntRange,
)

/**
 * 末尾日期短语 / 绝对日期的识别（Phase 18.2 的原逻辑，只是把结果换成带区间的形态）。
 */
private fun matchTailDate(
    text: String,
    today: LocalDate,
    zoneId: ZoneId,
    defaultTime: LocalTime,
): DateMatch? {
    if (text.isEmpty()) return null

    // 1) 先试绝对日期（2026-11-05 / 2026/11/05）—— 格式最明确，优先
    parseAbsoluteDate(text, today, zoneId, defaultTime)?.let { return it }

    // 2) 再试「末尾的日期短语」—— 从长到短，避免「3 天后」被「天后」抢走
    for (candidate in TAIL_PATTERNS) {
        val match = candidate.regex.find(text) ?: continue
        // 必须落在末尾（正则已用 $ 锚定，这里是双保险）
        if (match.range.last != text.lastIndex) continue

        val date = candidate.resolve(match, today) ?: continue
        val phrase = candidate.phraseRange(match) ?: continue
        return DateMatch(
            dueDate = date.atTime(defaultTime).atZone(zoneId).toInstant().toEpochMilli(),
            phraseRange = phrase,
        )
    }
    return null
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
): DateMatch? {
    val match = Regex("""^(.*?)\s*(\d{4})[-/](\d{1,2})[-/](\d{1,2})$""").find(text) ?: return null
    val groups = match.groupValues

    val date = runCatching {
        LocalDate.of(groups[2].toInt(), groups[3].toInt(), groups[4].toInt())
    }.getOrNull() ?: return null
    val yearGroup = match.groupRange(2) ?: return null

    return DateMatch(
        dueDate = date.atTime(defaultTime).atZone(zoneId).toInstant().toEpochMilli(),
        // 短语 = 年 ~ 日（中间的 - / 与月日分隔符都在匹配范围内）
        phraseRange = yearGroup.first..match.range.last,
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

    /**
     * 日期短语在匹配结果里的下标区间（闭区间）。
     *
     * ⚠️ 短语总是贴着文本末尾，所以**右端就是整个匹配的末尾**；
     * 左端要按模式各自的规则取（固定词 / 星期 / 月日各不相同）。
     */
    fun phraseRange(match: MatchResult): IntRange?

    /** 「今天 / 明天 / 后天 / 昨天」等固定词 */
    data class Fixed(override val regex: Regex, val offsetDays: Long) : TailPattern {
        override fun resolve(match: MatchResult, today: LocalDate): LocalDate =
            today.plusDays(offsetDays)

        override fun phraseRange(match: MatchResult): IntRange? {
            // ⚠️ 正则分两种写法：
            // - `^(.*?)(今天|今日|今晚)$` → 日期词在**分组 2**
            // - `^(.*?)大后天$` → 只有分组 1（前缀），日期词是匹配的尾巴
            val start = match.groupRange(2)?.first
                ?: match.groupRange(1)?.last?.plus(1)
                ?: return null
            return start..match.range.last
        }
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

        override fun phraseRange(match: MatchResult): IntRange? {
            val days = match.groupRange(2) ?: return null
            return days.first..match.range.last
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

        override fun phraseRange(match: MatchResult): IntRange? {
            // 短语 = 前缀之后那截（「下周一」/「本周三」/「周五」）
            val prefix = match.groupRange(1) ?: return null
            val weekday = match.groupRange(2) ?: return null
            return (prefix.last + 1)..weekday.last
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

        override fun phraseRange(match: MatchResult): IntRange? {
            val month = match.groupRange(2) ?: return null
            return month.first..match.range.last
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

/**
 * Phase 18.3：`#标签`。
 *
 * - `#` 必须在**词首**（前面是空白或整段开头）—— 否则「C#」「issue#12」会被误伤
 * - 名字是一串非空白字符，末尾的常见标点不收进名字里
 */
private val TAG_REGEX = Regex("""(?:^|\s)(#([^\s#~,，.。;；:：!！?？、]+))""")

/**
 * Phase 18.3：`~清单`。
 *
 * 规则跟 [TAG_REGEX] 一样（`~` 必须在词首）。
 */
private val LIST_REGEX = Regex("""(?:^|\s)(~([^\s#~,，.。;；:：!！?？、]+))""")
