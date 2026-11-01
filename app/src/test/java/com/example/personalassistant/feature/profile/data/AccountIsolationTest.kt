package com.example.personalassistant.feature.profile.data

import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.finance.data.seed.DefaultCategories
import com.example.personalassistant.feature.profile.data.local.AccountIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3：账户隔离在 JVM 层可证的语义
 *
 * 覆盖四件事（都能在 JVM 严格断言）：
 * 1. 实体默认 accountId —— 未显式赋值的写入不会掉出账户体系
 * 2. **旧备份 JSON 向后兼容** —— v8 及更早的 JSON 没有 accountId 字段，
 *    必须靠 @Serializable 默认值解码成 1L（默认账户），而不是抛 MissingField
 * 3. 当前账户变化驱动读流取值 —— 「切账户后 UI 自动换账本」的机制本体
 * 4. DefaultCategories 按账户各 seed 一套（Phase 5 起 10 个：8 支出 + 1 收入 + 1 转账；
 *    各带 accountId、id 留给 Room 分配）
 *
 * ## 关于测试写法的一条踩坑记录（别再走回头路）
 * 本文件初版用 `backgroundScope.launch { flow.toList(collected) }` + 手动
 * `testScheduler.advanceUntilIdle()` 想验证「活跃订阅连续收到 1→2→3」。
 * 实测 collected 为空：后台收集器在虚拟时间下拿不到中间值。
 * 现在改成**确定性取值**（每次切账户后用 first() 取一次），
 * 断言的是业务语义「读到的是当前账户的数据」；
 * flatMapLatest 自身的取消语义属 kotlinx 职责，不在此重复验证。
 *
 * ⚠️ 不覆盖：Room 真实查询有没有漏带 accountId（需要 instrumented 环境）。
 *    该风险由「DAO 逐条人工核对 + 白名单」（见 DEV_LOG）与
 *    [com.example.personalassistant.core.database.Migration_8_9StatementsTest] 兜底；
 *    androidTest 迁移真跑仍未完成，台账不标 ✅。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountIsolationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // ===== 1. 实体默认 accountId =====

    @Test
    fun `4 个记账实体默认 accountId 都是默认账户`() {
        assertEquals(
            AccountIds.DEFAULT_ACCOUNT_ID,
            TransactionEntity(amount = 1.0, type = 0, categoryId = 1, date = 0L).accountId,
        )
        assertEquals(
            AccountIds.DEFAULT_ACCOUNT_ID,
            CategoryEntity(name = "餐饮", type = 0).accountId,
        )
        assertEquals(
            AccountIds.DEFAULT_ACCOUNT_ID,
            MonthlyBudgetEntity(yearMonth = "2026-09", totalAmount = 100.0).accountId,
        )
        assertEquals(
            AccountIds.DEFAULT_ACCOUNT_ID,
            DailyPoolEntity(date = 0L, dailyBudget = 10.0, remainingAmount = 10.0).accountId,
        )
    }

    // ===== 2. 旧备份 JSON 向后兼容 =====

    @Test
    fun `v8 时代的流水 JSON 缺 accountId 字段应解码为默认账户`() {
        // 手工构造「没有 accountId 字段」的旧 JSON（等价于 Phase 3 之前导出的备份）
        val legacyJson = """
            [{"id":7,"amount":12.5,"type":0,"categoryId":3,"date":1725000000000,
              "note":"旧账","createdAt":1725000000001,"source":"手动","attachmentUri":null}]
        """.trimIndent()

        val decoded = json.decodeFromString(
            ListSerializer(TransactionEntity.serializer()),
            legacyJson,
        )

        assertEquals(1, decoded.size)
        assertEquals(AccountIds.DEFAULT_ACCOUNT_ID, decoded.single().accountId)
        assertEquals(7L, decoded.single().id)
        assertEquals("旧账", decoded.single().note)
    }

    @Test
    fun `v8 时代的预算与零钱池 JSON 同样回落到默认账户`() {
        val legacyBudget = """
            [{"id":1,"yearMonth":"2026-08","totalAmount":900.0,"isAutoRefresh":true,
              "carryOverEnabled":true,"warningThreshold":0.0,"createdAt":1,"updatedAt":2}]
        """.trimIndent()
        val legacyPool = """
            [{"id":1,"date":1725000000000,"dailyBudget":30.0,"remainingAmount":12.0,
              "totalSpentToday":18.0,"totalIncomeToday":0.0,"carryOver":0.0,"lastUpdated":1725000000001}]
        """.trimIndent()

        val budget = json.decodeFromString(
            ListSerializer(MonthlyBudgetEntity.serializer()), legacyBudget,
        ).single()
        val pool = json.decodeFromString(
            ListSerializer(DailyPoolEntity.serializer()), legacyPool,
        ).single()

        assertEquals(AccountIds.DEFAULT_ACCOUNT_ID, budget.accountId)
        assertEquals(AccountIds.DEFAULT_ACCOUNT_ID, pool.accountId)
        // 其余业务字段不能被默认值悄悄改掉
        assertEquals("2026-08", budget.yearMonth)
        assertEquals(900.0, budget.totalAmount, 0.0001)
        assertEquals(12.0, pool.remainingAmount, 0.0001)
    }

    @Test
    fun `带 accountId 的新 JSON 应原样读回而不是被覆盖成默认账户`() {
        val modern = listOf(
            TransactionEntity(id = 3L, accountId = 42L, amount = 5.0, type = 0, categoryId = 9L, date = 1L),
        )
        val encoded = json.encodeToString(ListSerializer(TransactionEntity.serializer()), modern)
        val decoded = json.decodeFromString(ListSerializer(TransactionEntity.serializer()), encoded)

        assertEquals(42L, decoded.single().accountId)
    }

    // ===== 3. 当前账户变化驱动读流 =====

    /**
     * 模拟 FinanceRepositoryImpl 读路径的形状：accountIdFlow.flatMapLatest { id -> 按 id 查 DAO }。
     *
     * 断言两件事：
     * - 切账户后读到的是新账户的数据（不是默认账户的旧数据）
     * - 每次都以当前 accountId 重新查 DAO（queried 序列证明 lambda 被重新调用，
     *   而不是把第一次的结果缓存复用）
     */
    @Test
    fun `切换当前账户后读流按新账户重查并返回新账本数据`() = runTest {
        val current = FakeCurrentAccountRepository()

        val queriedAccountIds = mutableListOf<Long>()
        val readFlow: Flow<List<String>> = current.currentAccountIdFlow()
            .flatMapLatest { accountId ->
                queriedAccountIds.add(accountId)
                flowOf(listOf("rows-of-$accountId"))
            }

        assertEquals(listOf("rows-of-1"), readFlow.first())

        current.setCurrentAccountId(2L)
        assertEquals(listOf("rows-of-2"), readFlow.first())

        current.setCurrentAccountId(3L)
        assertEquals(listOf("rows-of-3"), readFlow.first())

        assertEquals(listOf(1L, 2L, 3L), queriedAccountIds)
    }

    /** 零钱池这类派生读流同理：换账户后余额来自新账户，不是旧账户 */
    @Test
    fun `账户 id 变化会让派生的零钱池读流重新计算`() = runTest {
        val current = FakeCurrentAccountRepository()
        // 每个账户各有一份池子余额（模拟按 accountId 查表的结果）；查不到按 0，与实现一致
        val poolBalanceByAccount = mapOf(1L to 30.0, 2L to 88.0)

        val poolFlow = current.currentAccountIdFlow().map { poolBalanceByAccount[it] ?: 0.0 }

        assertEquals(30.0, poolFlow.first(), 0.0001)
        current.setCurrentAccountId(2L)
        assertEquals(88.0, poolFlow.first(), 0.0001)
    }

    @Test
    fun `Fake 当前账户仓库默认落默认账户并拒绝非正数 id`() = runTest {
        val current = FakeCurrentAccountRepository()
        assertEquals(AccountIds.DEFAULT_ACCOUNT_ID, current.currentAccountId())

        val result = runCatching { current.setCurrentAccountId(0L) }
        assertTrue("id<=0 应被拒绝", result.isFailure)
        assertEquals("失败的设置不应改动当前值", AccountIds.DEFAULT_ACCOUNT_ID, current.currentAccountId())
        assertTrue("失败的设置不应计数", current.setCallCount == 0)

        current.setCurrentAccountId(5L)
        assertEquals(5L, current.currentAccountId())
        assertTrue(current.setCallCount == 1)
        assertEquals(5L, current.lastSetAccountId)
    }

    // ===== 4. 分类按账户各一套 =====

    @Test
    fun `内置分类按账户生成 10 个并各自带 accountId`() {
        val forAccountOne = DefaultCategories.build(accountId = 1L)
        val forAccountTwo = DefaultCategories.build(accountId = 2L)

        // Phase 5 起每账户多一条**转账分类**（type=2）：转账流水的 categoryId 外键落点
        assertEquals("8 支出 + 1 收入 + 1 转账 = 10", 10, forAccountOne.size)
        assertEquals(10, forAccountTwo.size)

        assertTrue(
            "账户 1 的分类都应挂 accountId=1",
            forAccountOne.all { it.accountId == 1L },
        )
        assertTrue(
            "账户 2 的分类都应挂 accountId=2",
            forAccountTwo.all { it.accountId == 2L },
        )
        // 两账户内置分类同名（靠 accountId 区分），这正是 transactions 复合 FK 存在的理由
        assertEquals(
            forAccountOne.map { it.name }.sorted(),
            forAccountTwo.map { it.name }.sorted(),
        )
        assertNotEquals(
            "不同账户的分类不能共用 accountId",
            forAccountOne.first().accountId,
            forAccountTwo.first().accountId,
        )
        // id 都应为 0（交给 Room autoGenerate），否则会撞上别的账户已占用的分类 id
        assertTrue(forAccountOne.all { it.id == 0L })
        assertTrue(forAccountTwo.all { it.id == 0L })
    }

    @Test
    fun `不传参数时 build 默认落默认账户（兼容旧调用点）`() {
        val categories = DefaultCategories.build()
        assertEquals(10, categories.size)
        assertTrue(categories.all { it.accountId == AccountIds.DEFAULT_ACCOUNT_ID })
    }
}
