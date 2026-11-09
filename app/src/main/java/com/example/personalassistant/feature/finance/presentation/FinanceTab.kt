package com.example.personalassistant.feature.finance.presentation

/**
 * 记账首页的四个标签（Phase 26）。
 *
 * ## 为什么要把首页拆成标签
 *
 * 改造前记账页把六块内容纵向堆在同一个滚动列表里：
 * 账户切换条 → 搜索 → 今日剩余卡 → 零钱池卡 → 月报卡 → 跨账户预算 → 流水。
 * 一屏之内要滚很久才看得到流水，而「今天花了多少」和「这个月花在哪」本来就是
 * 两种不同的关注点，堆在一起谁也不突出。
 *
 * 拆成标签后每屏只讲一件事，同时把「今日剩余」留在最外层 —— 它是记账页的
 * 主数字，不管切到哪个标签都该看得见（见 [FinanceScreen] 里卡片的位置）。
 *
 * ## 顺序为什么是这样
 *
 * 流水排第一：记账页最高频的动作是「看/改一笔账」，不是看报表。
 */
enum class FinanceTab(val label: String) {
    /** 流水：搜索 + 按天分组的交易列表 */
    LEDGER("流水"),

    /** 报表：月度收支 / 分类 Top 5 / 折线 */
    REPORT("报表"),

    /** 零钱池：日终结转累加下来的钱（只读） */
    COIN_POOL("零钱池"),

    /** 预算：本月预算进度 + 跨账户总览 */
    BUDGET("预算"),
    ;

    companion object {
        /** 标签顺序（`entries` 已经是声明顺序，这里只是给 UI 一个显式入口） */
        val ordered: List<FinanceTab> = entries.toList()
    }
}