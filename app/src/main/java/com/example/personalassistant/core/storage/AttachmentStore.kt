package com.example.personalassistant.core.storage

import android.content.Context
import android.net.Uri
import com.example.personalassistant.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2：附件存储（图片压缩到 1080p 写入 filesDir/attachments）
 *
 * - 单例（Hilt @Singleton），整个应用共享
 * - saveAll 接受用户从 PhotoPicker 选出的 Uri 列表，每个都压缩为 1080p JPEG（85% 质量），
 *   文件名 `{uuid}.jpg`，存到 `context.filesDir/attachments/`（app 私有目录，无需运行时权限）
 * - deleteAll 接受 `TransactionEntity.attachmentUri` 字符串列表，删对应文件
 *
 * ## 设计权衡
 * - 选择 filesDir 而非 cacheDir：附件是"用户数据"，不能被系统清掉
 * - 选 JPEG 85% 而非 PNG：缩略图场景，肉眼几乎无差别，文件小一半
 * - 不做 EXIF 旋转：1080p 缩放后视觉差异小；后续 Phase 可加
 * - 不入库路径，只存 file:// URI：让 SQL 字段保持小，且 Uri.toString() 是稳定 ID
 *
 * ## Phase 4：编码与删除逻辑已抽到 [ImageEncoder]
 * 头像（[com.example.personalassistant.feature.profile.data.AvatarStorage]）与附件做的是同一件事，只差子目录名与最长边像素。
 * 曾考虑给本类构造函数加 `subdir` / `maxLongEdge` 参数来复用，**不可行**：
 * 本类是 `@Singleton open class ... @Inject constructor(...)`，Hilt 生成的
 * `AttachmentStore_Factory` 按全部参数调用；且 `app/src/test/.../FakeAttachmentStore.kt`
 * 继承本类并用命名参数调父类构造（它覆盖的 `saveAll` 正是 Phase 2 的被测路径）。
 * 故改为「提取顶层函数，两边各自调用」，**本类的公开签名一字未改**。
 */
@Singleton
open class AttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val attachmentDir: File
        get() = File(context.filesDir, ATTACHMENT_SUBDIR).apply {
            if (!exists()) mkdirs()
        }

    /**
     * 保存多个附件图片（压缩到 1080p JPEG）。
     *
     * - 跳过 null Uri 或无法解码的图片
     * - 返回保存后的 file:// URI 字符串列表（顺序与入参对应；失败的图片不在返回列表里）
     */
    open suspend fun saveAll(uris: List<Uri>): List<String> = withContext(dispatcher) {
        uris.mapNotNull { uri -> saveOne(uri) }
    }

    /**
     * 删除一个附件（按 file:// URI 字符串）。
     * - 找不到文件 / 不是 file:// URI 时静默 no-op
     * - 仅删除位于本 store 管理的目录下文件，避免误删（路径穿越保护）
     */
    open suspend fun delete(uriString: String) = withContext(dispatcher) {
        // 路径穿越保护（只删 attachments 子目录内的文件）在 [deleteFileInsideDir] 里实现。
        // 显式写 Unit：否则 withContext 会推断出 Boolean，改变本方法返回类型，
        // 连带弄坏 FakeAttachmentStore 的 `override ... = Unit`。
        deleteFileInsideDir(uriString, attachmentDir)
        Unit
    }

    /** 批量删除 */
    suspend fun deleteAll(uriStrings: List<String>) {
        uriStrings.forEach { delete(it) }
    }

    /** 删除附件目录下所有文件（清理用） */
    suspend fun deleteAllInDir() = withContext(dispatcher) {
        attachmentDir.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** 解码 + 缩放 + 落盘全部委派给 [encodeImageToJpeg]（与头像链路共用同一实现） */
    private fun saveOne(uri: Uri): String? =
        encodeImageToJpeg(
            resolver = context.contentResolver,
            uri = uri,
            dir = attachmentDir,
            maxLongEdge = ATTACHMENT_MAX_LONG_EDGE_PX,
        )
}