package com.example.personalassistant.feature.finance.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 C：零钱池存取（含负数）的 JVM 单测
 *
 * 覆盖：
 * - 首次存入 → 建行
 * - 再次存取 → 更新同一行（不新增行）
 * - 取出超过余额 → 余额为负（允许欠账）
 * - 余额可负 → 不被拦
 */
class CoinPoolRepositoryTest {

    @Test
    fun `no pool initially`() = runTest {
        val repo = FakeFinanceRepository()
        assertNull(repo.observeCoinPool().first())
    }

    @Test
    fun `deposit creates pool`() = runTest {
        val repo = FakeFinanceRepository()
        val balance = repo.adjustCoinPool(delta = 100.0, note = "第一笔")
        assertEquals(100.0, balance, 0.001)
        val pool = repo.observeCoinPool().first()
        assertNotNull(pool)
        assertEquals(100.0, pool!!.balance, 0.001)
        assertEquals("第一笔", pool.note)
    }

    @Test
    fun `deposit twice updates same row`() = runTest {
        val repo = FakeFinanceRepository()
        repo.adjustCoinPool(delta = 100.0)
        val idAfterFirst = repo.observeCoinPool().first()!!.id
        repo.adjustCoinPool(delta = 50.0)
        val pool = repo.observeCoinPool().first()!!
        assertEquals(150.0, pool.balance, 0.001)
        // 同一行，不是新增一行
        assertEquals(idAfterFirst, pool.id)
    }

    @Test
    fun `withdraw reduces balance`() = runTest {
        val repo = FakeFinanceRepository()
        repo.adjustCoinPool(delta = 100.0)
        val balance = repo.adjustCoinPool(delta = -30.0)
        assertEquals(70.0, balance, 0.001)
    }

    @Test
    fun `withdraw more than balance yields negative`() = runTest {
        val repo = FakeFinanceRepository()
        repo.adjustCoinPool(delta = 50.0)
        val balance = repo.adjustCoinPool(delta = -80.0)
        assertEquals(-30.0, balance, 0.001)
        assertTrue(repo.observeCoinPool().first()!!.isNegative)
    }

    @Test
    fun `set balance directly`() = runTest {
        val repo = FakeFinanceRepository()
        repo.adjustCoinPool(delta = 100.0)
        val balance = repo.setCoinPoolBalance(-12.5, note = "改错了")
        assertEquals(-12.5, balance, 0.001)
        val pool = repo.observeCoinPool().first()!!
        assertEquals(-12.5, pool.balance, 0.001)
        assertEquals("改错了", pool.note)
    }

    @Test
    fun `note null keeps previous note`() = runTest {
        val repo = FakeFinanceRepository()
        repo.adjustCoinPool(delta = 100.0, note = "原来的备注")
        repo.adjustCoinPool(delta = 10.0, note = null)
        assertEquals("原来的备注", repo.observeCoinPool().first()!!.note)
    }
}
