package com.example.personalassistant.core.lunar

/**
 * Phase 8 Sub-step 9 Step 5：农历日历抽象
 *
 * 月视图角标和后续「中历视角」共用入口。
 *
 * ## 设计原则
 * - 输入统一是「当天 00:00 的毫秒时间戳」，与 [com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell.date] 对齐。
 * - 输出 [LunarDate] 是项目自有类型，UI / 业务层只依赖这个接口，
 *   不直接 import `com.nlf.calendar.*`，便于测试替换和未来换实现。
 *
 * ## 显示策略
 * - [shortLabel] 把 [LunarDate] 折算成「UI 上要渲染的一行字」。
 *   优先级：
 *   1. 农历节日（春节、中秋、端午…）
 *   2. 公历节日（国庆节、元旦…）
 *   3. 月首（day == 1）→ 中文月份（「正月」「七月」）
 *   4. 其他 → 中文日（「初一」「廿八」「三十」）
 *
 *   节日的「关键节日永远显眼」是 plan 决策 1。
 *
 * - [displayLabel] 是「节日 > 月/日」的合并入口，UI 调用它就够了。
 *
 * @see DefaultLunarCalendar 默认实现（基于 cn.6tail:lunar）
 */
interface LunarCalendar {

    /**
     * 把「当天 00:00 的毫秒时间戳」转成 [LunarDate]。
     *
     * @param dateMillis 当天 00:00 的 epoch millis；时分秒会被忽略
     */
    fun toLunarDate(dateMillis: Long): LunarDate

    /**
     * 给 [LunarDate] 生成一行 UI 短标签：
     * - day == 1 → 中文月份（「正月」）
     * - 其他 → 中文日（「初一」「廿八」「三十」）
     *
     * 不含节日名。如需节日优先，请用 [displayLabel]。
     */
    fun shortLabel(date: LunarDate): String

    /**
     * 给 [LunarDate] 生成 UI 最终显示的标签（含节日优先级）：
     * 节日名 > 月/日短标签。
     *
     * 涟宝解读：UI 只调这一个函数就够了，决定显不显眼走这套优先级。
     */
    fun displayLabel(date: LunarDate): String
}