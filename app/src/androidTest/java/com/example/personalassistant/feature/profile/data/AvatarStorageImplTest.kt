package com.example.personalassistant.feature.profile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Phase 4：[AvatarStorageImpl] 的真实文件读写（instrumented）
 *
 * ⚠️ 本类所有 @Test 都**必须显式写 `: Unit`**。
 * `fun xxx() = runBlocking { ... }` 的返回类型由 lambda **最后一个表达式**推断，
 * 若结尾是一个返回非 Unit 的调用（例如 `file.delete()` 返回 Boolean），
 * 方法就不是 void，JUnit4 会在**跑任何用例之前**报：
 *   InvalidTestClassError: Method xxx() should be void
 * 表现为整个类只有1 条 `initializationError`，一条测试都不会执行。
 * 写第一条时踩到了，故统一加显式类型收敛问题到编译期。
 *
 * ## 为什么这条必须在设备上跑，JVM 测不到
 * [SetAccountAvatarUseCaseTest] 用两个 Fake 守住了**编排顺序**，但那是「假存储」：
 * `AvatarStorageImpl` 依赖 `ContentResolver` + `BitmapFactory` + `File`，
 * 项目虽开了 `isReturnDefaultValues`，那些 android API 在 JVM 里只会返回默认值 null ——
 * **成功路径在 JVM 里根本不存在**，连「有没有真的写出文件」都无法断言。
 *
 * ## 这里验的是 JVM 给不出来的四件事
 * 1. `saveAvatar` 真能解码并把 JPEG 写进 `filesDir/avatars/`
 * 2. **压缩真的生效**（最长边 ≤ 512；与附件的 1080 是不同参数，两边共用同一函数，
 *    参数字面传错的可能只能靠这类断言兜住）
 * 3. `delete` 真能删掉头像目录内的文件
 * 4. **目录隔离**：指向 `attachments/` 或 `filesDir` 根下的 URI，`delete` 必须拒绝
 *    —— 这正是本 Phase「头像不放 attachments/」那条决定所需的回归锁
 *
 * ## 为什么源图用 file:// 而不是 content://
 * `AvatarStorageImpl.saveAvatar` 只走 `contentResolver.openInputStream(uri)`，
 * 而 `Uri.fromFile` 产出的 file:// URI 在 Android 上由 `ContentResolver` 支持打开，
 * 无需真照片或 MediaStore。测的是「解码 + 缩放 + 落盘 + 目录保护」这条链，
 * 与图片来源是相册还是磁盘无关。
 *
 * 跑法：`./gradlew connectedDebugAndroidTest`（需 AVD `pm_api34`，环境记录见 DEV_LOG Phase 3）
 * 方法名一律 camelCase：androidTest 要过 D8，DEX 035 不允许方法名含空格（见 Phase 3 踩坑 15）。
 */
@RunWith(AndroidJUnit4::class)
class AvatarStorageImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val storage = AvatarStorageImpl(
        context = context,
        dispatcher = Dispatchers.IO,
    )

    @Test
    fun saveAvatarWritesScaledJpegIntoAvatarsDir(): Unit = runBlocking {
        val source = writeSourceImage(name = "avatar-src-large.jpg", width = 1600, height = 900)

        val saved = storage.saveAvatar(Uri.fromFile(source))

        assertNotNull("真实图片必须能解码并写出", saved)
        val uri = Uri.parse(saved!!)
        assertEquals("只应产出自有 file:// 路径", "file", uri.scheme)
        val out = File(uri.path!!)
        assertTrue("文件必须真的落盘", out.exists())
        assertEquals("必须落在 avatars 子目录，而不是 attachments", "avatars", out.parentFile?.name)
        assertTrue("扩展名应为 jpg", out.name.endsWith(".jpg"))

        // 压缩是否真生效：读回尺寸看最长边
        val decoded = BitmapFactory.decodeFile(out.absolutePath)
        assertNotNull("写出的必须是可解码图片", decoded)
        assertTrue(
            "最长边应被压到 512 以内，实际 ${decoded.width}x${decoded.height}",
            maxOf(decoded.width, decoded.height) <= AVATAR_MAX_LONG_EDGE,
        )
        decoded.recycle()
    }

    @Test
    fun saveAvatarReturnsNullForUndecodableSource(): Unit = runBlocking {
        // 模拟「用户挑了一张根本不是图片的文件」：保存必须失败，而不是抛给上层
        val junk = File(context.cacheDir, "not-an-image.jpg").apply {
            writeText("this is definitely not a jpeg")
        }

        val saved = storage.saveAvatar(Uri.fromFile(junk))

        assertNull("无法解码时应返回 null，由 UseCase 据此保持原状", saved)
    }

    @Test
    fun deleteRemovesFileInsideAvatarsDir(): Unit = runBlocking {
        val source = writeSourceImage(name = "avatar-src-todelete.jpg", width = 800, height = 600)
        val saved = requireNotNull(storage.saveAvatar(Uri.fromFile(source)))
        val target = File(Uri.parse(saved).path!!)
        assertTrue("前置条件：文件先要存在", target.exists())

        storage.delete(saved)

        assertTrue("头像目录内的文件应被删除", !target.exists())
    }

    @Test
    fun deleteRefusesToTouchFilesOutsideAvatarsDir(): Unit = runBlocking {
        // 这条是「头像独立目录」决定的回归锁：
        // 若将来有人图省事把头像写进 attachments/，本用例会失败并提醒代价
        // （AttachmentStore.deleteAllInDir() 会清空 attachments 整个目录）。
        val attachmentDir = File(context.filesDir, "attachments").apply { mkdirs() }
        val victim = File(attachmentDir, "a-transaction-photo.jpg").apply {
            writeBytes(encodeJpeg(16))
        }
        assertTrue("前置条件：附件文件先要存在", victim.exists())

        storage.delete(Uri.fromFile(victim).toString())
        assertTrue(
            "指向 attachments/ 的 URI 必须被拒绝",
            victim.exists(),
        )

        // 更直接的目录外尝试：filesDir 根下的文件
        val outside = File(context.filesDir, "not-an-avatar.txt").apply { writeText("x") }
        storage.delete(Uri.fromFile(outside).toString())
        assertTrue("filesDir 根下的文件同样不得被删", outside.exists())

        outside.delete()
        victim.delete()
        // 收尾的 delete() 返回 Boolean —— 正是它曾把本方法推断成非 Unit，现靠显式 : Unit 兑住
    }

    // ===== helpers =====

    /** 生成一张纯色 **JPEG** 写到 cache 下，作为 PhotoPicker 产物的替身 */
    private fun writeSourceImage(name: String, width: Int, height: Int): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        assertTrue("测试前置：源图必须写成功", file.exists())
        return file
    }

    private fun encodeJpeg(size: Int): ByteArray {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        return out.toByteArray()
    }

    @After
    fun cleanUpAvatars() {
        // 用例之间不共享状态：清掉自己产生的头像文件
        File(context.filesDir, "avatars").listFiles()?.forEach { it.delete() }
    }

    private companion object {
        /**
         * 与 `ImageEncoder.AVATAR_MAX_LONG_EDGE_PX` 同值。
         * 那个常量是 internal 且在另一个源集不可见，所以这里只能复制字面值 ——
         * 若将来调头像尺寸而忘了同步，第一个用例会失败来提醒。
         */
        const val AVATAR_MAX_LONG_EDGE = 512
    }
}
