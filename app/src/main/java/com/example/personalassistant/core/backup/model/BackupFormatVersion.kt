package com.example.personalassistant.core.backup.model

/**
 * Phase 8 Sub-step 4：备份格式版本常量
 *
 * - FORMAT_VERSION：导出/导入格式版本号（递增，破坏性变更 +1）
 * - DB_VERSION：写入此备份时 App 数据库的 schema version
 * - APPLICATION：应用包标识（防止跨应用误读备份）
 *
 * 备份文件结构：
 * ```
 * cyrene-backup-{timestamp}.zip
 * ├── manifest.json     (格式版本 + 创建时间 + 数据库版本)
 * ├── checksums.json    (各表 JSON 的 sha256)
 * └── data/
 *     ├── courses.json
 *     ├── semesters.json
 *     ├── tasks.json
 *     ├── sub_tasks.json
 *     ├── tags.json
 *     ├── task_tag_cross_ref.json
 *     ├── events.json
 *     ├── transactions.json
 *     ├── categories.json
 *     ├── monthly_budgets.json
 *     ├── daily_pools.json
 *     └── holidays.json     ← Phase 8 Sub-step 7 新增（v6 起）
 * ```
 */
object BackupFormatVersion {
    /** 当前备份格式版本 */
    const val FORMAT_VERSION: Int = 1

    /** App 数据库 schema version（备份时的 schema 版本）*/
    const val DB_VERSION: Int = 6

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
