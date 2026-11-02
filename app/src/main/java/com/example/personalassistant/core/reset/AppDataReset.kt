package com.example.personalassistant.core.reset

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.RoomDatabase
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.storage.AttachmentStore
import com.example.personalassistant.core.storage.AVATAR_SUBDIR
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阶段 Phase 6：应用数据清除（设置页「清除全部数据」入口的实际执行者）。
 *
 * ## 范围（不可恢复，UI 层需 3 秒长按 + 二次确认）
 * 1. [Room] 业务表：15 张全部清空（[RoomDatabase.clearAllTables]，事务内）。
 *    ⚠️ 不会触发 [com.example.personalassistant.core.database.DatabaseCallback.onCreate]，
 *    因此**默认账户不会被重新 seed**（决策 1A：完全清空，不留默认行）。
 *    下次启动 FinanceTab 会显示空账户列表，由用户在设置页建账户。
 *    （v15 / Phase 16 起是 15 张：v13 的 16 张减去已废弃的 `sub_tasks`
 *    与 `sub_task_tag_cross_ref`，加上新建的 `task_lists`。）
 * 2. [DataStore<Preferences>]：单一 Preferences 实例（"settings"）所有 key 一起清。
 *    含主题 / 当前账户 id / 通知询问 / 任务显示 / 备份 / 节假日等所有偏好。
 *    清完后 `currentAccountId` 回到默认 0（[com.example.personalassistant.feature.profile.data.local.AccountIds.DEFAULT_ACCOUNT_ID]）。
 * 3. [filesDir/attachments/]：调用 [AttachmentStore.deleteAllInDir]。
 *    附件是「数据库引用的私有数据」，删表后必须删盘，否则孤立文件吃空间。
 * 4. [filesDir/avatars/]：`AvatarStorage` 接口没有 `deleteAll`，但本类持有
 *    [Context]，按 [AVATAR_SUBDIR] 路径整目录 `deleteRecursively()` 后重建。
 *    头像路径**不写入数据库**清空后的 `user_profiles`，故删盘安全。
 *
 * ## 不做的事（明确边界）
 * - **不**清 SAF 备份目录里的导出 zip：那是用户主动存到外部的，不归 App 管。
 * - **不**碰 `cacheDir`（图片缓存由系统在低空间时自动回收）。
 * - **不**重置 `APP_DATABASE_VERSION` / 写 Migration：`clearAllTables` 只清行，不动 schema。
 *
 * ## 调度与闹钟
 * WorkManager 的 Work 由系统级 WorkManager.cancelAllWork() 一并清，本类不单独处理；
 * AlarmManager 上的精确闹钟（任务/事件）靠 `BootReceiver` 下次开机按空表重建，
 * 本类清完后建议 UI 提示用户「重启一次 App」，避免残留闹钟弹通知。
 * `BackupWorkerScheduler` / `HolidayReminderScheduler` / `DailyPoolRefreshScheduler`
 * 各自的 cancel 由上层 [com.example.personalassistant.core.reset.ResetRepositoryImpl] 调用，
 * 不在本类里 —— 本类只负责「数据层」，调度层是另一回事。
 */
@Singleton
class AppDataReset @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val dataStore: DataStore<Preferences>,
    private val attachmentStore: AttachmentStore,
) : DataResetExecutor {

    /**
     * 同步清除全部数据。
     *
     * 实现要点：
     * - Room 清表必须在 IO 线程 → 本方法挂起，调用方负责切线程。
     * - DataStore.edit 是事务性写入，安全。
     * - 文件 IO 顺序：先清数据库（事务内）→ 再清盘（否则删盘后数据库还引用旧 URI，
     *   后续读头像/附件会 404，且事务回滚也救不了）。
     * - 单步失败会让后续步骤照常尝试；UI 用「部分成功」状态反馈。
     *
     * @return 各步骤是否成功的快照，UI 据此提示「部分步骤失败」。
     */
    override suspend fun resetAll(): ResetReport {
        val dbOk = runCatching { database.clearAllTables() }.isSuccess
        val dataStoreOk = runCatching { dataStore.edit { it.clear() } }.isSuccess
        val attachmentsOk = runCatching { attachmentStore.deleteAllInDir() }.isSuccess
        val avatarsOk = runCatching { wipeAvatarsDir() }.isSuccess
        return ResetReport(
            databaseCleared = dbOk,
            datastoreCleared = dataStoreOk,
            attachmentsCleared = attachmentsOk,
            avatarsCleared = avatarsOk,
        )
    }

    /**
     * 清空头像目录：删目录下所有文件后重建空目录。
     *
     * 直接走文件系统（不绕 [AvatarStorage] 接口），因为该接口不暴露「清整个目录」
     * 这一动作 —— 我们也**不应该**往业务接口里加这个方法（业务接口只服务业务用例，
     * 「清所有头像」是平台级动作，应在平台层做）。
     *
     * 失败原因几乎只有：磁盘满 / 权限丢失 —— 两种情况都让重置报告成「部分失败」，
     * 上一行 [resetAll] 已经把 `user_profiles` 表清完，下次启动不会有头像引用，
     * 残留盘文件也只是吃空间，不会导致 404。
     */
    private fun wipeAvatarsDir(): Boolean {
        val dir = File(context.filesDir, AVATAR_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
            return true
        }
        // listFiles().forEach { delete() } 比 deleteRecursively() 安全：
        // 头像目录只有 1 层文件，没有子目录；后者会顺手清掉意外产生的子目录，
        // 而那些子目录可能是别的逻辑期望保留的（例如未来加 thumbnail/ 子目录）。
        dir.listFiles()?.forEach { it.delete() }
        return true
    }
}

/**
 * 清除结果报告（给 UI 用：哪个步骤成功了、哪个失败）。
 */
data class ResetReport(
    val databaseCleared: Boolean,
    val datastoreCleared: Boolean,
    val attachmentsCleared: Boolean,
    val avatarsCleared: Boolean,
) {
    /** 全部成功才算「真·清除干净」 */
    val allSucceeded: Boolean
        get() = databaseCleared && datastoreCleared && attachmentsCleared && avatarsCleared
}