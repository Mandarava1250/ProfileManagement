package com.example.personalassistant.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * [SettingsRepository] 的月截止日设置
 *
 * ## 为什么单独一个测试类
 * DataStore 的读写发生在**真实 IO 线程**，`runTest` 的虚拟时间推不动它。
 * 所以这里用 `runBlocking` 真等 —— 等的是"值真的落到磁盘"，不靠猜 sleep 多久够。
 *
 * 截止日=1 时旧行为完全不变（自然月），这是本功能最关键的回归点。
 */
class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        // UUID 后缀保证每个用例独立文件，避免 DataStore 多实例冲突
        val file = File(tempFolder.newFolder(), "settings-${UUID.randomUUID()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        repository = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `默认截止日是 1 号（与本功能上线前一致）`() = runBlocking {
        assertEquals(1, repository.financeMonthCutoffDay.first())
    }

    @Test
    fun `默认生效 ym 是哨兵值 1970-01`() = runBlocking {
        assertEquals("1970-01", repository.financeMonthCutoffEffectiveFrom.first())
    }

    @Test
    fun `写入截止日后能读回新值`() = runBlocking {
        repository.setFinanceMonthCutoff(day = 15, effectiveFromYm = "2026-10")
        withTimeout(5_000) {
            assertEquals(15, repository.financeMonthCutoffDay.first { it == 15 })
        }
    }

    @Test
    fun `写入同时保存生效 ym`() = runBlocking {
        repository.setFinanceMonthCutoff(day = 28, effectiveFromYm = "2026-10")
        withTimeout(5_000) {
            assertEquals("2026-10", repository.financeMonthCutoffEffectiveFrom.first { it == "2026-10" })
        }
    }

    @Test
    fun `截止日超出 1-28 直接抛异常（不做静默夹取）`() = runBlocking {
        var thrown = false
        try {
            repository.setFinanceMonthCutoff(day = 29, effectiveFromYm = "2026-10")
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertEquals("29 号应当被拒绝", true, thrown)
    }
}
