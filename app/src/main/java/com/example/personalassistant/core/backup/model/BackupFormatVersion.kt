package com.example.personalassistant.core.backup.model

import com.example.personalassistant.core.database.APP_DATABASE_VERSION

/**
 * Phase 8 Sub-step 4：备份格式版本常量
 *
 * - FORMAT_VERSION：导出/导入格式版本号（递增，破坏性变更 +1）
 * - DB_VERSION：写入此备份时 App 数据库的 schema version
 * - APPLICATION：应用包标识（防止跨应用误读备份）
 *
 * ⚠️ DB_VERSION 与 [com.example.personalassistant.core.database.AppDatabase.version] 同步维护。
 *    Phase 3 之前它长期停在 7，而实际 schema 已到 8 —— 一处未被发现的老漂移，
 *    随 Phase 3（v9）一并修正；现在它直接引用 [APP_DATABASE_VERSION]，不会再漂。
 *
 * 备份文件结构：
 * ```
 * cyrene-backup-{timestamp}.zip
 * ├── manifest.json     (格式版本 + 创建时间 + 数据库版本)
 * ├── checksums.json    (各表 JSON 的 sha256)
 * └── data/
 *     ├── user_profiles.json    ← Phase 3 新增（账户清单，记账数据归属根表）
 *     ├── semesters.json
 *     ├── courses.json
 *     ├── tags.json
 *     ├── task_lists.json       ← Phase 16 新增（清单表，tasks.listId 的父表）
 *     ├── tasks.json
 *     ├── task_tag_cross_ref.json
 *     ├── events.json
 *     ├── event_categories.json ← Phase 16 新增（日历事件分类，events.categoryId 的父表）
 *     ├── categories.json
 *     ├── monthly_budgets.json
 *     ├── daily_pools.json
 *     ├── transactions.json
 *     ├── holidays.json         ← Phase 8 Sub-step 7 新增（v6 起）
 *     ├── student_profile.json  ← Phase 16 新增（「我」档案，一账户一行）
 *     ├── habits.json           ← Phase 23 新增（习惯本体）
 *     ├── habit_checkins.json   ← Phase 23 新增（习惯打卡记录）
 *     └── coin_pools.json       ← 批次 C 新增（零钱池）
 * ```
 */
object BackupFormatVersion {
    /**
     * 当前备份格式版本
     *
     * Phase 3 没有升它：按 [BackupManifest] 的增量规则「新增表只往 tables 加一项，
     * FORMAT_VERSION 不必升」，user_profiles 属于新增表；
     * 记账 4 表新增 accountId 字段则靠 `@Serializable` 默认值向后兼容（旧 JSON 缺该字段 → 落 1L）。
     *
     * Phase 16：**升到 2**。理由是这次动到了「字段删除」这条红线 ——
     * `sub_tasks` 表整张消失，[BackupPayload.subTasks] 字段被删除。
     * 按 [BackupManifest] 的增量规则「删除字段：FORMAT_VERSION + 1」，
     * 旧备份（formatVersion=1）解出来的 payload 少了这张表，
     * 导入端必须能识别出「这份备份来自 sub_tasks 还在的年代」。
     *
     * 新增表（task_lists / event_categories / student_profile）本身不构成升版理由 ——
     * 它们都有 `@Serializable` 默认值，旧 JSON 缺字段时解出空列表即可。
     *
     * Phase 23：**没升它**（仍是 2）。理由与 Phase 3 的 user_profiles 完全一样 ——
     * 本次只是**新增两张表**（habits / habit_checkins），没有删字段、没有改字段语义，
     * 按 [BackupManifest] 的增量规则「新增表：往 tables 加一项，FORMAT_VERSION 不必升」。
     * 两个新字段都有 `@Serializable` 默认值（`= emptyList()`），
     * 旧备份（formatVersion ≤ 2）解出来是空列表 → 导入端据此判断「备份没带这两张表」
     * → 不清空用户现有的习惯数据。
     *
     * 批次 C：**同样没升它**（仍是 2）。新增 `coin_pools` 属于「新增表」，
     * `BackupPayload.coinPools` 有 `= emptyList()` 默认值 → 旧备份解出空列表 →
     * 导入端按「备份里有没有这个条目」判断要不要清表（见 `BackupImporter`）。
     *
     * ⚠️ 什么时候才需要升 v3：删字段 / 改字段语义 / 改表集合的**既有**表结构时。
     */
    const val FORMAT_VERSION: Int = 2

    /**
     * App 数据库 schema version（备份时的 schema 版本）
     *
     * ⚠️ Phase 5：必须与 [com.example.personalassistant.core.database.APP_DATABASE_VERSION] 同步。
     * 它由 BackupFormatVersionTest 直接引用那个常量比对，不是比对字面量。
     *
     * Phase 16（v15）：新建 `task_lists` + `tasks` 加 7 列 + `sub_tasks` 整表并入 `tasks`。
     * 这里**不再写字面量** —— 直接引用 [APP_DATABASE_VERSION]，
     * 以后 schema 升版只要动数据库那边一个常量，备份这边自动跟上（老漂移的根治）。
     */
    const val DB_VERSION: Int = APP_DATABASE_VERSION

    /** 应用包名（写入 manifest 用于跨应用保护）*/
    const val APPLICATION: String = "com.example.personalassistant"

    /** manifest 文件名 */
    const val FILE_MANIFEST: String = "manifest.json"

    /** checksums 文件名 */
    const val FILE_CHECKSUMS: String = "checksums.json"

    /** data 目录 */
    const val DIR_DATA: String = "data"

    /** ZIP 文件名前缀 */
    const val ZIP_PREFIX: String = "cyrene-backup-"

    /** ZIP 文件名后缀 */
    const val ZIP_SUFFIX: String = ".zip"

    /**
     * 生成 ZIP 文件名（如 cyrene-backup-20260907-181500.zip）
     */
    fun defaultZipName(timestamp: Long = System.currentTimeMillis()): String {
        // 时间戳格式化为本地时间 yyyyMMdd-HHmmss
        val formatter = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        return "$ZIP_PREFIX${formatter.format(java.util.Date(timestamp))}$ZIP_SUFFIX"
    }
}
