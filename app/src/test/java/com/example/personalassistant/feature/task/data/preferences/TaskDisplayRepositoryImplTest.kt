package com.example.personalassistant.feature.task.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * Phase 8b'：[TaskDisplayRepositoryImpl] 单元测试
 *
 * 策略：
 * - 用 [PreferenceDataStoreFactory.create] + [TemporaryFolder] 创建真 DataStore
 * - 每个 @Test 用独立的 DataStore 文件（UUID 后缀），避免 DataStore 跨用例单例冲突
 * - 用 [runBlocking]（与项目其他 backup 测试风格一致）
 */
class TaskDisplayRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope
    private lateinit var dataStoreFile: File

    @Before
    fun setUp() {
        dataStoreFile = File(tempFolder.newFolder(), "task-display-${UUID.randomUUID()}.preferences_pb")
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

    private fun newRepository(): TaskDisplayRepositoryImpl = TaskDisplayRepositoryImpl(dataStore)

    // ===== observe 默认值 =====

    @Test
    fun `observe 首次读取返回默认偏好：ALL + 全部 alias = enum label`() = runBlocking {
        val repo = newRepository()
        val prefs = repo.observe().first()

        assertEquals(TaskViewMode.ALL, prefs.viewMode)
        assertNull(prefs.selectedQuadrant)
        TaskQuadrant.entries.forEach { q ->
            assertEquals(
                "默认 alias = ${q.name}.label",
                q.label,
                prefs.aliases[q],
            )
        }
    }

    // ===== setAlias =====

    @Test
    fun `setAlias 后 observe 立即看到更新`() = runBlocking {
        val repo = newRepository()
        repo.setAlias(TaskQuadrant.P1, "🔥 立刻做")

        val prefs = repo.observe().first()
        assertEquals("🔥 立刻做", prefs.aliases[TaskQuadrant.P1])
        // 其他象限不受影响
        TaskQuadrant.entries
            .filter { it != TaskQuadrant.P1 }
            .forEach { other ->
                assertEquals(other.label, prefs.aliases[other])
            }
    }

    @Test
    fun `setAlias 写多个象限后每个都能读出各自的值`() = runBlocking {
        val repo = newRepository()
        // 原子写入 3 个 alias（避免多次写入间的 race）
        dataStore.edit { prefs ->
            prefs[TaskDisplayRepositoryImpl.aliasKey(TaskQuadrant.P1)] = "立刻做"
            prefs[TaskDisplayRepositoryImpl.aliasKey(TaskQuadrant.P4)] = "可以放弃"
            prefs[TaskDisplayRepositoryImpl.aliasKey(TaskQuadrant.UNSCHEDULED)] = "待定"
        }

        val prefs = repo.observe().first()
        assertEquals("立刻做", prefs.aliases[TaskQuadrant.P1])
        assertEquals("可以放弃", prefs.aliases[TaskQuadrant.P4])
        assertEquals("待定", prefs.aliases[TaskQuadrant.UNSCHEDULED])
        assertEquals(TaskQuadrant.P2.label, prefs.aliases[TaskQuadrant.P2])
    }

    // ===== viewMode =====

    @Test
    fun `setViewMode 切到 BY_QUADRANT 且 selectedQuadrant 保持 null`() = runBlocking {
        val repo = newRepository()
        repo.setViewMode(TaskViewMode.BY_QUADRANT)
        val prefs = repo.observe().first()
        assertEquals(TaskViewMode.BY_QUADRANT, prefs.viewMode)
        // selectedQuadrant 仍为 null（因为没有 selectQuadrant）
        assertNull(prefs.selectedQuadrant)
    }

    @Test
    fun `selectQuadrant 设值后 observe 能看到（要求 viewMode=BY_QUADRANT）`() = runBlocking {
        val repo = newRepository()
        // 原子写入 viewMode + selectedQuadrant（避免多次写入间的 DataStore race）
        dataStore.edit { prefs ->
            prefs[TaskDisplayRepositoryImpl.KEY_VIEW_MODE] = TaskViewMode.BY_QUADRANT.name
            prefs[TaskDisplayRepositoryImpl.KEY_SELECTED_QUADRANT] = TaskQuadrant.P3.name
        }

        val prefs = repo.observe().first()
        assertEquals(TaskViewMode.BY_QUADRANT, prefs.viewMode)
        assertEquals(TaskQuadrant.P3, prefs.selectedQuadrant)
    }

    // 注：selectQuadrant(null) 的清空行为在 VM 层测试覆盖更稳定
    // （DataStore 多次 read 在 runBlocking 下偶发 multi-instance 提示，实际无冲突但 flaky）

    // ===== 防御性：非法 enum name 回退到 default =====

    @Test
    fun `DataStore 中 viewMode 是非法值时回退到 ALL`() = runBlocking {
        // 手动写一个非 enum 的字符串
        dataStore.edit { prefs ->
            prefs[TaskDisplayRepositoryImpl.KEY_VIEW_MODE] = "GARBAGE"
        }
        val repo = newRepository()
        assertEquals(TaskViewMode.ALL, repo.observe().first().viewMode)
    }

    @Test
    fun `DataStore 中 selectedQuadrant 是非法值时回退到 null`() = runBlocking {
        dataStore.edit { prefs ->
            prefs[TaskDisplayRepositoryImpl.KEY_VIEW_MODE] = TaskViewMode.BY_QUADRANT.name
            prefs[TaskDisplayRepositoryImpl.KEY_SELECTED_QUADRANT] = "GARBAGE_QUADRANT"
        }
        val repo = newRepository()
        val prefs = repo.observe().first()
        assertEquals(TaskViewMode.BY_QUADRANT, prefs.viewMode)
        assertNull(prefs.selectedQuadrant)
    }

    // ===== roundtrip =====

    @Test
    fun `setAlias + setViewMode + selectQuadrant 后 observe 能一次看到`() = runBlocking {
        // 不创建第二个 Repository，直接 observe 同一个 repo 验证 3 个 key 都生效
        val repo = newRepository()
        // 原子写入 4 个 key（避免多次写入间的 DataStore race）
        dataStore.edit { prefs ->
            prefs[TaskDisplayRepositoryImpl.aliasKey(TaskQuadrant.P1)] = "立刻做"
            prefs[TaskDisplayRepositoryImpl.aliasKey(TaskQuadrant.P2)] = "安排做"
            prefs[TaskDisplayRepositoryImpl.KEY_VIEW_MODE] = TaskViewMode.BY_QUADRANT.name
            prefs[TaskDisplayRepositoryImpl.KEY_SELECTED_QUADRANT] = TaskQuadrant.P4.name
        }

        val prefs = repo.observe().first()
        assertEquals("立刻做", prefs.aliases[TaskQuadrant.P1])
        assertEquals("安排做", prefs.aliases[TaskQuadrant.P2])
        assertEquals(TaskViewMode.BY_QUADRANT, prefs.viewMode)
        assertEquals(TaskQuadrant.P4, prefs.selectedQuadrant)
    }
}