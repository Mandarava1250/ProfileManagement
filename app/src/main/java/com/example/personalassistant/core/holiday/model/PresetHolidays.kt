package com.example.personalassistant.core.holiday.model

/**
 * Phase 8 Sub-step 7：预置节假日数据（2025 + 2026）
 *
 * ## 数据策略
 * - 覆盖**两年**：本年（2026）+ 去年（2025）。每年 12 月下一年的放假通知发布后，
 *   会通过 [com.example.personalassistant.core.holiday.data.HolidaySyncGateway] 拉取最新数据。
 * - **不做通用农历换算**：农历节日（春节/端午/中秋/重阳/腊八/元宵/七夕）均按
 *   国务院当年实际放假口径硬编码到对应公历日，附带 `note` 标记农历日期。
 * - **调休**（WORKDAY）按国务院公布的「X 月 X 日补班」日期硬编码。
 *
 * ## 如何扩年
 * 在 [PRESETS] 末尾追加 `Triple(2027, ...)` 即可，无需改其它代码。
 * 首次安装时通过 [com.example.personalassistant.core.database.DatabaseCallback] seed。
 *
 * ## epochDay 速查
 * - 2025-01-01 = 20089, 2025-12-31 = 20453
 * - 2026-01-01 = 20454, 2026-12-31 = 20818
 */
object PresetHolidays {

    /** 全部预置数据 */
    val PRESETS: List<HolidayEntity> = buildList {
        // ===== 2025 年 =====
        addAll(holidays2025())
        // ===== 2026 年 =====
        addAll(holidays2026())
    }

    // ──────────────────────────── 2025 ────────────────────────────

    private fun holidays2025(): List<HolidayEntity> = listOf(
        // --- 1 月：元旦 + 腊八 + 春节
        e(20095, "腊八节", HolidayType.OBSERVANCE, 2025, note = "农历腊月初八"),
        e(20114, "春节前调休", HolidayType.WORKDAY, 2025, note = "国务院补班"),
        e(20116, "春节", HolidayType.HOLIDAY, 2025, note = "除夕"),
        e(20117, "春节", HolidayType.HOLIDAY, 2025, note = "农历正月初一"),
        e(20118, "春节", HolidayType.HOLIDAY, 2025),
        e(20119, "春节", HolidayType.HOLIDAY, 2025),
        e(20120, "春节", HolidayType.HOLIDAY, 2025),
        e(20121, "春节", HolidayType.HOLIDAY, 2025),
        e(20122, "春节", HolidayType.HOLIDAY, 2025),
        e(20123, "春节", HolidayType.HOLIDAY, 2025, note = "正月初七"),
        e(20131, "元宵节", HolidayType.OBSERVANCE, 2025, note = "农历正月十五"),

        // --- 4 月：清明
        e(20182, "清明节", HolidayType.HOLIDAY, 2025),
        // 劳动节前调休 4/27 周日补班
        e(20205, "劳动节前调休", HolidayType.WORKDAY, 2025, note = "国务院补班"),
        // 5 月：劳动节
        e(20209, "劳动节", HolidayType.HOLIDAY, 2025),
        e(20210, "劳动节", HolidayType.HOLIDAY, 2025),
        e(20211, "劳动节", HolidayType.HOLIDAY, 2025),
        // 端午 5/31 农历五月初五
        e(20239, "端午节", HolidayType.HOLIDAY, 2025, note = "农历五月初五"),

        // --- 8 月：七夕
        e(20329, "七夕节", HolidayType.OBSERVANCE, 2025, note = "农历七月初七"),
        // 国庆前调休 9/28 周日补班
        e(20359, "国庆前调休", HolidayType.WORKDAY, 2025, note = "国务院补班"),
        // --- 10 月：国庆 + 中秋
        e(20362, "国庆节", HolidayType.HOLIDAY, 2025),
        e(20363, "国庆节", HolidayType.HOLIDAY, 2025),
        e(20364, "国庆节", HolidayType.HOLIDAY, 2025),
        e(20365, "国庆节", HolidayType.HOLIDAY, 2025),
        e(20366, "国庆节", HolidayType.HOLIDAY, 2025),
        e(20367, "中秋节", HolidayType.HOLIDAY, 2025, note = "农历八月十五"),
        e(20368, "国庆节", HolidayType.HOLIDAY, 2025),
        // 重阳
        e(20390, "重阳节", HolidayType.OBSERVANCE, 2025, note = "农历九月初九"),
        // --- 12 月：冬至
        e(20443, "冬至", HolidayType.OBSERVANCE, 2025),
    )

    // ──────────────────────────── 2026 ────────────────────────────

    private fun holidays2026(): List<HolidayEntity> = listOf(
        // --- 1 月：元旦 + 腊八
        e(20454, "元旦", HolidayType.HOLIDAY, 2026),
        e(20455, "元旦", HolidayType.HOLIDAY, 2026),
        e(20456, "元旦", HolidayType.HOLIDAY, 2026),
        e(20479, "腊八节", HolidayType.OBSERVANCE, 2026, note = "农历腊月初八"),
        // --- 2 月：春节 + 调休
        e(20498, "春节前调休", HolidayType.WORKDAY, 2026, note = "国务院补班"),
        e(20501, "春节", HolidayType.HOLIDAY, 2026, note = "除夕"),
        e(20502, "春节", HolidayType.HOLIDAY, 2026, note = "农历正月初一"),
        e(20503, "春节", HolidayType.HOLIDAY, 2026),
        e(20504, "春节", HolidayType.HOLIDAY, 2026),
        e(20505, "春节", HolidayType.HOLIDAY, 2026),
        e(20506, "春节", HolidayType.HOLIDAY, 2026),
        e(20507, "春节", HolidayType.HOLIDAY, 2026, note = "正月初七"),
        // --- 3 月：元宵
        e(20515, "元宵节", HolidayType.OBSERVANCE, 2026, note = "农历正月十五"),

        // --- 4 月：清明
        e(20548, "清明节", HolidayType.HOLIDAY, 2026),
        // --- 5 月：劳动节
        e(20574, "劳动节", HolidayType.HOLIDAY, 2026),
        e(20575, "劳动节", HolidayType.HOLIDAY, 2026),
        e(20576, "劳动节", HolidayType.HOLIDAY, 2026),
        e(20577, "劳动节", HolidayType.HOLIDAY, 2026),
        e(20578, "劳动节", HolidayType.HOLIDAY, 2026),
        // --- 6 月：端午（农历五月初五）
        e(20623, "端午节", HolidayType.HOLIDAY, 2026, note = "农历五月初五"),

        // --- 8 月：七夕
        e(20684, "七夕节", HolidayType.OBSERVANCE, 2026, note = "农历七月初七"),
        // 国庆前调休 9/27 周日补班
        e(20721, "中秋节", HolidayType.HOLIDAY, 2026, note = "农历八月十五"),
        e(20723, "国庆前调休", HolidayType.WORKDAY, 2026, note = "国务院补班"),
        // --- 10 月：国庆
        e(20727, "国庆节", HolidayType.HOLIDAY, 2026),
        e(20728, "国庆节", HolidayType.HOLIDAY, 2026),
        e(20729, "国庆节", HolidayType.HOLIDAY, 2026),
        e(20730, "国庆节", HolidayType.HOLIDAY, 2026),
        e(20731, "国庆节", HolidayType.HOLIDAY, 2026),
        e(20732, "国庆节", HolidayType.HOLIDAY, 2026),
        e(20733, "国庆节", HolidayType.HOLIDAY, 2026),
        // --- 11 月：重阳
        e(20775, "重阳节", HolidayType.OBSERVANCE, 2026, note = "农历九月初九"),
        // --- 12 月：冬至
        e(20809, "冬至", HolidayType.OBSERVANCE, 2026),
    )

    /** 构造一条 HolidayEntity 的小帮手 */
    private fun e(
        epochDay: Long,
        name: String,
        type: HolidayType,
        year: Int,
        note: String? = null,
    ) = HolidayEntity(
        id = 0L,
        dateEpochDay = epochDay,
        name = name,
        type = type,
        year = year,
        note = note,
    )

    /** 数据覆盖年份范围 */
    val YEAR_RANGE: IntRange = 2025..2026
}