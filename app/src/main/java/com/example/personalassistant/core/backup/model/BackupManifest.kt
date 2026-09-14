package com.example.personalassistant.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Phase 8 Sub-step 4：备份清单（manifest）
 *
 * 写入 ZIP 顶层 manifest.json。包含：
 * - 应用包名（防止跨应用误读）
 * - 备份格式版本（解析侧校验）
 * - 数据库 schema version（导入侧记录"备份源 schema 版本"）
 * - 创建时间（毫秒时间戳）
 * - 包含的表清单
 *
 * 增量式扩展规则：
 * - 新增表：往 [tables] 加一项，FORMAT_VERSION 不必升
 * - 字段变更：FORMAT_VERSION + 1，旧版本解析兼容（忽略未知字段）
 * - 删除字段：FORMAT_VERSION + 1，旧版本解析容错（缺失字段用默认值）
 */
@Serializable
data class BackupManifest(
    val application: String,
    val formatVersion: Int,
    val dbVersion: Int,
    val createdAt: Long,
    val tables: List<String>,
) {
    companion object {
        /** manifest 的固定顺序表清单（导入侧按此顺序清理+插入以保证外键依赖） */
        val DEFAULT_TABLES: List<String> = listOf(
            // Phase 3：账户表排最前 —— 记账 4 表的 accountId 指向它，必须先就位
            "user_profiles",
            "semesters",
            "courses",
            "tags",
            "tasks",
            "sub_tasks",
            "task_tag_cross_ref",
            "events",
            "categories",
            "monthly_budgets",
            "daily_pools",
            "transactions",
            // Phase 8 Sub-step 7：节假日表（v6 新增）
            "holidays",
        )
    }
}
