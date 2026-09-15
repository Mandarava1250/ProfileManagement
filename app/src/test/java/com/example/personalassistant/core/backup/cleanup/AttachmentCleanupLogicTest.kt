package com.example.personalassistant.core.backup.cleanup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Phase 8 Sub-step 5（plan 8c.2）：[AttachmentCleanupLogic] JVM 单测
 *
 * ## 覆盖的 5 条用例
 * 1. 空目录 + 空引用 → 0 个孤儿
 * 2. 3 文件全被引用 → 0 个孤儿
 * 3. 3 文件，1 个孤儿 → 只删 1 个
 * 4. 3 文件全孤儿 → 全删
 * 5. 子目录不被列入（listFiles 默认非递归）—— 防止误删头像子目录
 *
 * ## 不测什么（明确边界）
 * - `findOrphans` 内部文件名解析细节由测试覆盖输入
 * - 删除失败重试是 Worker 层的逻辑（[AttachmentCleanupWorker.runCatching]），不在本类
 * - DAO 层的 `allAttachmentUris` 由 Room instrumented 测覆盖（不在本测试范围）
 */
class AttachmentCleanupLogicTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * 构造逻辑实例 + 给定一组磁盘文件名 + 给定一组引用 URI 字符串，
     * 把磁盘文件先建好。
     */
    private fun setup(
        diskFileNames: List<String>,
        referencedUris: List<String>,
    ): Pair<AttachmentCleanupLogic, MutableList<String>> {
        // 建磁盘文件
        diskFileNames.forEach { name ->
            File(tempFolder.root, name).writeText("fake-image-bytes")
        }

        // 记录 deleter 调用 + 真实删除（生产 lambda 也是真删）
        val deleted = mutableListOf<String>()

        val logic = AttachmentCleanupLogic(
            attachmentDir = tempFolder.root,
            referencedUrisProvider = { referencedUris },
            fileDeleter = { fileName ->
                deleted.add(fileName)
                File(tempFolder.root, fileName).delete()
            },
        )
        return logic to deleted
    }

    @Test
    fun emptyDirAndEmptyReferencesYieldsNoOrphans() = runTest {
        val (logic, deleted) = setup(diskFileNames = emptyList(), referencedUris = emptyList())

        val orphans = logic.findOrphans()
        assertEquals("空目录不应有孤儿", emptyList<String>(), orphans)

        val removed = logic.cleanup()
        assertEquals("cleanup 应返回 0", 0, removed)
        assertEquals("deleter 不应被调用", 0, deleted.size)
    }

    @Test
    fun allFilesReferencedYieldsNoOrphans() = runTest {
        val (logic, deleted) = setup(
            diskFileNames = listOf("a.jpg", "b.jpg", "c.jpg"),
            referencedUris = listOf(
                "file:///data/user/0/x/files/attachments/a.jpg",
                "file:///data/user/0/x/files/attachments/b.jpg",
                "file:///data/user/0/x/files/attachments/c.jpg",
            ),
        )

        val orphans = logic.findOrphans()
        assertEquals("全被引用不应有孤儿", emptyList<String>(), orphans)

        val removed = logic.cleanup()
        assertEquals(0, removed)
        assertEquals(0, deleted.size)

        // 文件还在
        listOf("a.jpg", "b.jpg", "c.jpg").forEach {
            assertTrue("被引用文件不应被删：$it", File(tempFolder.root, it).exists())
        }
    }

    @Test
    fun oneOrphanAmongThreeFilesIsDeleted() = runTest {
        // b.jpg 是孤儿（没出现在 referenced）
        val (logic, deleted) = setup(
            diskFileNames = listOf("a.jpg", "b.jpg", "c.jpg"),
            referencedUris = listOf(
                "file:///data/user/0/x/files/attachments/a.jpg",
                "file:///data/user/0/x/files/attachments/c.jpg",
            ),
        )

        val orphans = logic.findOrphans()
        assertEquals("只有 b.jpg 应被识别为孤儿", listOf("b.jpg"), orphans)

        val removed = logic.cleanup()
        assertEquals(1, removed)
        assertEquals("deleter 应被调用 1 次", listOf("b.jpg"), deleted)

        assertTrue("a.jpg 应保留", File(tempFolder.root, "a.jpg").exists())
        assertTrue("c.jpg 应保留", File(tempFolder.root, "c.jpg").exists())
    }

    @Test
    fun allFilesOrphanAreAllDeleted() = runTest {
        val (logic, deleted) = setup(
            diskFileNames = listOf("a.jpg", "b.jpg", "c.jpg"),
            referencedUris = emptyList(),
        )

        val orphans = logic.findOrphans()
        assertEquals(
            "3 个文件都应被识别为孤儿",
            setOf("a.jpg", "b.jpg", "c.jpg"),
            orphans.toSet(),
        )

        val removed = logic.cleanup()
        assertEquals(3, removed)
        assertEquals("deleter 应被调用 3 次", 3, deleted.size)

        // 目录还在但文件全没了
        listOf("a.jpg", "b.jpg", "c.jpg").forEach {
            assertTrue("孤儿文件应被删：$it", !File(tempFolder.root, it).exists())
        }
        assertTrue("目录本身应保留", tempFolder.root.exists())
    }

    @Test
    fun subdirectoriesAreNotListedAsOrphans() = runTest {
        // 头像保存在 avatars/ 子目录（AttachmentStore 同款模式 —— 子目录隔离）
        // 子目录本身不应被列入孤儿列表，否则可能误删头像
        val avatarsDir = File(tempFolder.root, "avatars").apply { mkdirs() }
        File(avatarsDir, "profile.png").writeText("avatar-bytes")

        // attachments 根下有真孤儿
        File(tempFolder.root, "real-orphan.jpg").writeText("orphan-bytes")

        val (logic, deleted) = setup(
            diskFileNames = listOf("real-orphan.jpg"), // 只列出根目录文件
            referencedUris = emptyList(),
        )

        val orphans = logic.findOrphans()
        assertEquals(
            "子目录中的头像不应被列入孤儿",
            listOf("real-orphan.jpg"),
            orphans,
        )

        val removed = logic.cleanup()
        assertEquals(1, removed)

        // 头像还在
        assertTrue("子目录中的头像必须保留", File(avatarsDir, "profile.png").exists())
    }
}