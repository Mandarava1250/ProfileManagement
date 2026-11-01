package com.example.personalassistant.core.utils

import android.content.Context
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常处理器
 *
 * ## 为什么需要它
 *
 * 没有它的话，崩溃栈只在 logcat 里，而且 Android 的 logcat 会被其它日志冲掉、
 * 复制的时候也容易截断（线上那次排查就因为截断，只看到第一个 Suppressed 异常，
 * 漏掉了另外两列不匹配的列）。
 *
 * 装了它之后，**每次崩溃的完整栈都会落到 App 私有目录的 `crash.log`**，
 * 一条命令就能拉出来：
 *
 * ```bash
 * adb shell run-as com.example.personalassistant.debug cat files/crash.log > crash.txt
 * ```
 *
 * ## 设计要点
 *
 * - **同步写文件**：崩溃时进程随时可能被杀，异步写会丢。写文件很小，代价可接受。
 * - **追加而非覆盖**：保留最近 N 次崩溃，能看出「偶发」还是「必现」。
 * - **链式调用原 handler**：不吞掉系统默认行为（弹崩溃对话框 / 上报 ANR）。
 * - **截断保护**：单次栈超过 [MAX_STACK_CHARS] 就截断，避免超大栈把文件撑爆。
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val LOG_FILE_NAME = "crash.log"
    private const val MAX_STACK_CHARS = 32_000
    private const val MAX_HISTORY = 10

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * 安装全局 handler。在 [android.app.Application.onCreate] 最开始调用。
     */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(context, thread, throwable)
            } catch (e: Throwable) {
                // 写日志本身失败也不能让崩溃处理变成新的崩溃源
                Log.e(TAG, "failed to write crash log", e)
            }
            // 交回系统默认行为
            previous?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "🛡 global uncaught exception handler installed")
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { sw ->
            PrintWriter(sw).use { throwable.printStackTrace(it) }
        }.toString()

        val entry = buildString {
            appendLine("=".repeat(72))
            appendLine("time    : ${timeFormat.format(Date())}")
            appendLine("thread  : ${thread.name}")
            appendLine("process : ${context.packageName}")
            appendLine("version : ${appVersion(context)}")
            appendLine("-".repeat(72))
            append(stack.take(MAX_STACK_CHARS))
            if (stack.length > MAX_STACK_CHARS) {
                appendLine("... (truncated, total ${stack.length} chars)")
            }
        }

        val file = context.filesDir.resolve(LOG_FILE_NAME)
        trimIfTooLarge(file)
        file.appendText(entry)
        Log.e(TAG, "💥 crash captured -> ${file.absolutePath}\n$entry")
    }

    private fun appVersion(context: Context): String = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.versionCode})"
    } catch (e: Throwable) {
        "unknown"
    }

    /** 只保留最近 [MAX_HISTORY] 次崩溃，避免文件无限增长 */
    private fun trimIfTooLarge(file: java.io.File) {
        if (!file.exists() || file.length() < 512 * 1024) return
        try {
            val lines = file.readLines()
            val keep = lines.takeLast(MAX_HISTORY * 200)
            file.writeText(keep.joinToString("\n"))
        } catch (e: Throwable) {
            Log.w(TAG, "failed to trim crash log", e)
        }
    }
}
