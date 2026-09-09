package com.example.personalassistant.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.personalassistant.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

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
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return@withContext
        if (uri.scheme != "file") return@withContext
        val path = uri.path ?: return@withContext
        val file = File(path)
        // 路径穿越保护：只在 attachments 子目录内的文件才能删除
        if (!file.parentFile?.canonicalPath.equals(attachmentDir.canonicalPath)) return@withContext
        if (file.exists()) file.delete()
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

    private fun saveOne(uri: Uri): String? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        input.use { stream ->
            val original = BitmapFactory.decodeStream(stream) ?: return@runCatching null
            val scaled = scaleDown(original, MAX_LONG_EDGE_PX)
            val filename = "${UUID.randomUUID()}.jpg"
            val file = File(attachmentDir, filename)
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (scaled !== original) original.recycle()
            scaled.recycle()
            Uri.fromFile(file).toString()
        }
    }.getOrNull()

    private fun scaleDown(src: Bitmap, maxLongEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longEdge = max(w, h)
        if (longEdge <= maxLongEdge) return src
        val scale = maxLongEdge.toDouble() / longEdge
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    companion object {
        private const val ATTACHMENT_SUBDIR = "attachments"
        private const val MAX_LONG_EDGE_PX = 1080
        private const val JPEG_QUALITY = 85
    }
}