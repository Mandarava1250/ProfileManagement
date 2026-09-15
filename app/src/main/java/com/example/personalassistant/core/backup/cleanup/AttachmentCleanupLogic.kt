package com.example.personalassistant.core.backup.cleanup

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 5（plan 8c.2）：附件孤儿文件清理逻辑
 *
 * ## 背景
 * Phase 2（[com.example.personalassistant.core.storage.AttachmentStore]）把用户选的图片
 * 压缩到 1080p JPEG 写入 `filesDir/attachments/`。删除流水时**没有联动删附件文件**，
 * 长期积累孤儿文件占据 app 私有空间。
 *
 * ## 策略
 * 拉全库 transactions.attachmentUri 全集（[referencedUrisProvider]），
 * 与 `attachmentDir` 下的文件名做差集：
 * - 文件名（URI 末段）出现在 referenced 集合里 → 保留（被引用）
 * - 否则 → 删（孤儿）
 *
 * ## 测试友好
 * 三个依赖（dir / referenced / deleter）都通过构造参数注入，**纯 JVM 单测即可**：
 * - `attachmentDir`：`File`（JUnit @Rule TemporaryFolder）
 * - `referencedUrisProvider`：`suspend () -> List<String>`（lambda 写死返回值）
 * - `fileDeleter`：`suspend (String) -> Unit`（lambda 记录调用）
 *
 * 不引入 [kotlinx.coroutines.Dispatchers.IO] 等运行期 dispatcher，方便测。
 *
 * ## 与 Worker 的关系
 * 本类是纯逻辑，被 [AttachmentCleanupWorker] 包装后跑在 WorkManager 上。
 * 测试只需覆盖本类，Worker 集成测试留给 Phase 9+（需要 HiltWorkerFactory 全套）。
 */
@Singleton
class AttachmentCleanupLogic @Inject constructor(
    private val attachmentDir: File,
    private val referencedUrisProvider: suspend () -> List<String>,
    private val fileDeleter: suspend (String) -> Unit,
) {

    /**
     * 列出所有孤儿文件（不删除）。
     *
     * @return 孤儿文件名（不含路径）列表
     */
    suspend fun findOrphans(): List<String> {
        if (!attachmentDir.exists()) return emptyList()

        // 取全库引用集合（转为文件名末段，因为 transactions 存的是 file:// URI 末段）
        val referencedFileNames = referencedUrisProvider()
            .mapNotNull { uriString -> extractFileName(uriString) }
            .toSet()

        // 列磁盘目录下的所有文件
        val onDisk = attachmentDir.listFiles().orEmpty()
            .filter { it.isFile }

        // 差集
        return onDisk
            .filter { it.name !in referencedFileNames }
            .map { it.name }
    }

    /**
     * 列出孤儿 + 删除，返回删除成功的数量。
     */
    suspend fun cleanup(): Int {
        val orphans = findOrphans()
        orphans.forEach { fileName ->
            fileDeleter(fileName)
        }
        return orphans.size
    }

    /**
     * 从 `file:///.../attachments/abc.jpg` 提取 `abc.jpg`。
     * 防御性写法：取最后一段路径分隔符之后的部分。
     */
    private fun extractFileName(uriString: String): String? {
        val trimmed = uriString.substringAfterLast('/')
        return trimmed.takeIf { it.isNotEmpty() }
    }
}