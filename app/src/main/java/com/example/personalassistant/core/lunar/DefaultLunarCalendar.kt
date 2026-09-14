package com.example.personalassistant.core.lunar

import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 9 Step 5：[LunarCalendar] 的默认实现，基于 [cn.6tail:lunar]。
 *
 * ## 库版本
 * - cn.6tail:lunar:1.7.5（来自 Maven Central）
 * - 支持年份范围 1900-2099（覆盖本项目全生命周期）
 *
 * ## 关键 API
 * - [Solar.fromYmd]：从公历 y/m/d 构造 Solar
 * - [Solar.getLunar]：得到 Lunar 对象
 * - [Lunar.getMonthInChinese]：[正月/二月/.../腊月/闰X月]
 * - [Lunar.getDayInChinese]：[初一/初二/.../廿八/三十]
 * - [Lunar.getFestivals]：农历传统节日（春节、中秋、端午、元宵...）
 * - [Solar.getFestivals]：公历节日（国庆节、元旦、情人节...）
 *
 * ## 边界
 * - 输入 [dateMillis] 当作系统时区的「本地时间」，所以从 epoch millis → LocalDate 时
 *   用 [ZoneId.systemDefault()]。测试里可以用 fake clock 注入不同的 millis 即可。
 * - lunar-java 是纯 Java 库，可在 JVM 单元测试直接调用，不需要 Android runtime。
 */
@Singleton
class DefaultLunarCalendar @Inject constructor() : LunarCalendar {

    override fun toLunarDate(dateMillis: Long): LunarDate {
        val localDate = Instant.ofEpochMilli(dateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val solar = Solar.fromYmd(localDate.year, localDate.monthValue, localDate.dayOfMonth)
        val lunar = solar.lunar

        // lunar-java 的 monthInChinese 不带「月」字（"正"/"冬"/"腊"/"闰正"…），
        // 我们在 domain 层统一补全成 "正月"/"冬月"/"腊月"/"闰正月"，
        // 让 UI 渲染层只关心字符串本身，不用关心「这个库到底带不带月」。
        val rawMonth = lunar.monthInChinese
        val monthInChinese = if (rawMonth.startsWith("闰")) {
            // 闰月原始值形如 "闰正" / "闰二"，补 "月" 变成 "闰正月" / "闰二月"
            rawMonth + "月"
        } else {
            rawMonth + "月"
        }

        return LunarDate(
            year = lunar.year,
            month = lunar.month,
            day = lunar.day,
            // 月份字符串已含「闰」前缀，可直接判断
            isLeapMonth = rawMonth.startsWith("闰"),
            monthInChinese = monthInChinese,
            dayInChinese = lunar.dayInChinese,
            festivals = lunar.festivals.orEmpty(),
            solarFestivals = solar.festivals.orEmpty(),
        )
    }

    override fun shortLabel(date: LunarDate): String {
        return if (date.day == 1) date.monthInChinese else date.dayInChinese
    }

    override fun displayLabel(date: LunarDate): String {
        // 节日优先：农历节日 > 公历节日 > 月/日短标签
        date.festivals.firstOrNull()?.let { return it }
        date.solarFestivals.firstOrNull()?.let { return it }
        return shortLabel(date)
    }
}