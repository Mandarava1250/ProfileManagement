package com.example.personalassistant.core.lunar

/**
 * Phase 8 Sub-step 9 Step 5：农历日期模型
 *
 * 用纯 Kotlin 数据类封装 lunar-java 库返回的字段，
 * 让上层（domain / presentation）只依赖本项目自有类型，方便单测替换。
 *
 * ## 字段
 * - [year] / [month] / [day]：农历年月日（month = 1..12 或闰月 1..12）
 * - [isLeapMonth]：是否闰月
 * - [monthInChinese]：中文月份（「正月」「七月」「闰四月」），月初展示更醒目
 * - [dayInChinese]：中文日（「初一」「廿八」「三十」）
 * - [festivals]：农历传统节日（春节、中秋、端午…），可能多个（库里顺序固定）
 * - [solarFestivals]：公历节日（国庆节、元旦…）
 *
 * 涟宝解读：month/day 仅作为结构化数据保留；UI 显示请用 [LunarCalendar.shortLabel]。
 */
data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
    val monthInChinese: String,
    val dayInChinese: String,
    val festivals: List<String>,
    val solarFestivals: List<String>,
)