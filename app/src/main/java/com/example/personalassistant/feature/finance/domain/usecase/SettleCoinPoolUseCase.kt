package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.CoinPoolSettlement
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 批次 D：零钱池日切结算
 *
 * ## 结算到哪一天 —— **昨天**
 *
 * 结算的语义是「今日剩余在**今日结束后**自动存入零钱池」。
 * 结算发生在 00:01（Worker）/ 任意时刻（冷启动），那一刻「今天」才刚开始，
 * 还没过完 —— 拿「今天」去结算等于把还没发生的一天也结掉了。
 *
 * 所以结算目标固定是 **D = 触发日 − 1 天**：
 *
 * | 触发时刻 | 结算到 |
 * |---|---|
 * | 10/6 00:01（Worker） | 10/5 |
 * | 10/6 15:00（冷启动） | 10/5 |
 * | 10/6 23:59 | 10/5 |
 *
 * 「无论何时触发都结算到昨天」这条规则很干净：
 * 一天只会被结算一次（幂等由水位线 + 结算流水唯一索引保证），
 * 也不会出现「同一天按两个口径算两遍」。
 *
 * ## 补算
 *
 * 上次结算到哪天由 `coin_pools.lastSettledDate` 记着；
 * 这次会把「水位线 + 1 天」到 D 之间**漏掉的日子**一次性补齐。
 * 补算的细节（没有 `daily_pools` 行的日子怎么办）见
 * [com.example.personalassistant.feature.finance.data.repository.FinanceRepositoryImpl.settleCoinPool]。
 */
class SettleCoinPoolUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    /**
     * 执行结算（结算到昨天）
     *
     * @param now 触发时刻（默认当前时间）
     * @return 本次结算的结果
     */
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): CoinPoolSettlement {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return repository.settleCoinPool(yesterdayOf(today))
    }

    /**
     * 结算目标日 = 昨天
     *
     * 单独暴露是为了能单测（「给一个今天，断言算出的是昨天」）。
     */
    fun yesterdayOf(today: LocalDate): LocalDate = today.minusDays(1)
}
