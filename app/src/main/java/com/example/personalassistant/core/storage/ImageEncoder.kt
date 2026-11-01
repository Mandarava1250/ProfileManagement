package com.example.personalassistant.core.storage

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Phase 4：把「解码 → 缩放 → JPEG 落盘」与「按目录校验后删除」抽成共用实现。
 *
 * ## 为什么要抽出来
 * [AttachmentStore]（Phase 2，记账附件）与 [com.example.personalassistant.feature.profile.data.AvatarStorage]（Phase 4，账户头像）做的是同一件事：
 * 用户选一张图 → 压成 JPEG 写进 filesDir 的某个子目录 → 存 `file://` URI 字符串。
 * 差别只有三个参数：**子目录名**、**最长边像素**、**JPEG 质量**。
 *
 * 原先打算给 [AttachmentStore] 的构造函数加 `subdir` / `maxLongEdge` 两个参数来复用，
 * 实测不可行，两个硬约束：
 * 1. 它是 `@Singleton open class ... @Inject constructor(...)`，Hilt 生成的 Java 代码按
 *    **全部构造参数**调用；加参数会让 `AttachmentStore_Factory` 编译失败。
 * 2. `app/src/test/.../FakeAttachmentStore.kt` **继承**了它并用命名参数调父类构造，
 *    而它覆盖的 `saveAll` 正是被测路径 —— 动签名会牵动 Phase 2 的既有测试。
 * 所以改成「提取函数、两边调用」，[AttachmentStore] 的公开签名**一字不改**。
 *
 * ## 为什么放在 core/storage 而不是 feature 下
 * 它不携带任何业务语义（既不懂附件也不懂头像），和 [AttachmentStore] 同级。
 */

/** 附件（记账照片）用：最长边 1080，质量 85 —— 与 Phase 2 保持完全一致 */
internal const val ATTACHMENT_SUBDIR: String = "attachments"
internal const val ATTACHMENT_MAX_LONG_EDGE_PX: Int = 1080

/** 头像用：最长边 512，质量 85 —— UI 里最大显示 ~48dp，512px 已足够清晰且文件小 */
internal const val AVATAR_SUBDIR: String = "avatars"
internal const val AVATAR_MAX_LONG_EDGE_PX: Int = 512

/** 两处共用同一质量，避免「同一条链路两种压缩参数」的隐性差异 */
internal const val JPEG_QUALITY: Int = 85

/**
 * 解码 [uri] → 等比缩到 [maxLongEdge] 以内 → 以 `{uuid}.jpg` 写入 [dir]。
 *
 * 返回写成的 `file://` URI 字符串；**任何一步失败都返回 null**（不抛异常）：
 * 无法解码（非图片 / 权限失效）、目录不可写、编码失败都会走这里。
 * 调用方（[AttachmentStore] / [com.example.personalassistant.feature.profile.data.AvatarStorageImpl]）据此决定「不写库、不删旧文件」。
 *
 * 注意：[dir] 必须已存在（由调用方的目录 getter 负责 mkdirs）。
 */
internal fun encodeImageToJpeg(
    resolver: ContentResolver,
    uri: Uri,
    dir: File,
    maxLongEdge: Int,
): String? = runCatching {
    val input = resolver.openInputStream(uri) ?: return@runCatching null
    input.use { stream ->
        val original = BitmapFactory.decodeStream(stream) ?: return@runCatching null
        val scaled = scaleDown(original, maxLongEdge)
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (scaled !== original) original.recycle()
        scaled.recycle()
        Uri.fromFile(file).toString()
    }
}.getOrNull()

/**
 * 删除 [dir] 内的单个文件（按 `file://` URI 字符串）。
 *
 * 路径穿越保护：只有**父目录正好是 [dir]** 的文件才允许删除，
 * 因此传错 URI（指向 filesDir 根、别的应用目录、或 `content://`）都会静默 no-op。
 *
 * @return 是否真的删掉了一个存在的文件（用于测试与日志；调用方通常可忽略）
 */
internal fun deleteFileInsideDir(uriString: String, dir: File): Boolean = runCatching {
    val uri = Uri.parse(uriString)
    if (uri.scheme != "file") return@runCatching false
    val path = uri.path ?: return@runCatching false
    val file = File(path)
    if (file.parentFile?.canonicalPath != dir.canonicalPath) return@runCatching false
    file.exists() && file.delete()
}.getOrDefault(false)

/** 等比缩小；长边已不超过 [maxLongEdge] 时原样返回（避免无谓的重采样损失） */
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
