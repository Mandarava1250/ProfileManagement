package com.example.personalassistant.feature.settings.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * Phase 8 Sub-step 6：ThemeRepositoryImpl 单元测试
 *
 * 策略：
 * - 用 [PreferenceDataStoreFactory.create] + [TemporaryFolder] 提供的临时文件
 *   创建真 DataStore 实例（不需要 Robolectric）
 * - 用 [runBlocking] 替代 runTest（项目其他 backup 测试也是这种风格）
 * - 每个测试用独立的 DataStore 文件（UUID 后缀），避免 DataStore 单例冲突：
 *   DataStore 内部会对同一文件加锁，多个 instance 写入时报 "Unable to rename"
 */
class ThemeRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope
    private lateinit var dataStoreFile: File

    @Before
    fun setUp() {
        // 关键：UUID 后缀保证每个 @Test 拿到独立文件，避免 DataStore 跨用例的
        // 单例冲突（"there are multiple instances of DataStore for this file"）
        dataStoreFile = File(tempFolder.newFolder(), "theme-${UUID.randomUUID()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { dataStoreFile },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newRepository(): ThemeRepositoryImpl = ThemeRepositoryImpl(dataStore)

    @Test
    fun `observe 返回默认偏好（mode=SYSTEM, dynamicColor=false）`() = runBlocking {
        val repo = newRepository()
        val prefs = repo.observe().first()
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
        // Phase 14：动态色默认关闭 —— 樱花粉 2.0 的主色是设计好的，
        // 让系统取色会把这套粉调打乱。
        assertEquals(false, prefs.dynamicColor)
    }

    @Test
    fun `setMode 后 observe 立即返回新值`() = runBlocking {
        val repo = newRepository()
        repo.setMode(ThemeMode.DARK)
        val prefs = repo.observe().first()
        assertEquals(ThemeMode.DARK, prefs.themeMode)
    }

    @Test
    fun `setDynamicColor 后 observe 立即返回新值`() = runBlocking {
        val repo = newRepository()
        repo.setDynamicColor(false)
        val prefs = repo.observe().first()
        assertEquals(false, prefs.dynamicColor)
    }

    @Test
    fun `兼容旧 DataStore 里的 theme_mode 字符串`() = runBlocking {
        // 模拟升级前用户 DataStore 里直接写入的 "light" 字符串
        dataStore.edit { prefs ->
            prefs[ThemeRepositoryImpl.KEY_THEME_MODE] = "light"
        }
        val repo = newRepository()
        val prefs = repo.observe().first()
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)
    }
}
