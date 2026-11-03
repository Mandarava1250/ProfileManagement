package com.example.personalassistant.feature.task.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personalassistant.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 22.2：搜索命中标签名 / 清单名
 *
 * 跑法：需要设备或模拟器（`./gradlew connectedDebugAndroidTest`）。
 * 纯 JVM 单测跑不了 —— 项目没引 Robolectric，而 Room 查询必须落在真 SQLite 上。
 */
@RunWith(AndroidJUnit4::class)
class TaskSearchDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var taskDao: TaskDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskDao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `标签名能命中清单项`() = runTest {
        // 收集箱（tasks.listId 默认指向它）
        db.taskListDao().insert(
            TaskListEntity(id = 1L, nameNormalized = "inbox", displayName = "收集箱", isSystem = true),
        )
        val tagId = db.tagDao().insert(
            TagEntity(nameNormalized = "work", displayName = "Work", colorHex = 0xFFFF69B4L),
        )
        val taskId = taskDao.insert(TaskEntity(title = "写周报"))
        db.taskTagDao().attach(TaskTagCrossRef(taskId = taskId, tagId = tagId))

        val rows = taskDao.searchTasks("%Work%").first()

        assertEquals(1, rows.size)
        assertEquals("写周报", rows.first().task.title)
        assertEquals("Work", rows.first().matchedTagNames)
    }

    @Test
    fun `清单名能命中清单项`() = runTest {
        db.taskListDao().insert(
            TaskListEntity(id = 1L, nameNormalized = "inbox", displayName = "收集箱", isSystem = true),
        )
        db.taskListDao().insert(
            TaskListEntity(id = 2L, nameNormalized = "gongzuo", displayName = "工作"),
        )
        taskDao.insert(TaskEntity(title = "写周报", listId = 2L))

        val rows = taskDao.searchTasks("%工作%").first()

        assertEquals(1, rows.size)
        assertEquals("工作", rows.first().matchedListName)
    }

    @Test
    fun `标题命中时不带命中来源`() = runTest {
        db.taskListDao().insert(
            TaskListEntity(id = 1L, nameNormalized = "inbox", displayName = "收集箱", isSystem = true),
        )
        taskDao.insert(TaskEntity(title = "写周报"))

        val rows = taskDao.searchTasks("%周报%").first()

        assertEquals(1, rows.size)
        assertEquals(null, rows.first().matchedTagNames)
        assertEquals(null, rows.first().matchedListName)
    }

    @Test
    fun `命中多个标签时只出现一次`() = runTest {
        db.taskListDao().insert(
            TaskListEntity(id = 1L, nameNormalized = "inbox", displayName = "收集箱", isSystem = true),
        )
        val tagA = db.tagDao().insert(
            TagEntity(nameNormalized = "work", displayName = "Work", colorHex = 0xFFFF69B4L),
        )
        val tagB = db.tagDao().insert(
            TagEntity(nameNormalized = "work2", displayName = "Work Two", colorHex = 0xFFFF69B4L),
        )
        val taskId = taskDao.insert(TaskEntity(title = "写周报"))
        db.taskTagDao().attach(TaskTagCrossRef(taskId = taskId, tagId = tagA))
        db.taskTagDao().attach(TaskTagCrossRef(taskId = taskId, tagId = tagB))

        val rows = taskDao.searchTasks("%Work%").first()

        // ⚠️ 关键：EXISTS 而不是 JOIN —— JOIN 会让这个任务出现两次
        assertEquals(1, rows.size)
        assertTrue(rows.first().matchedTagNames!!.contains("Work"))
    }
}
