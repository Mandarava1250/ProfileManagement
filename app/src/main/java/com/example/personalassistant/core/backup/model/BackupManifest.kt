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
        /**
         * manifest 的固定顺序表清单（导入侧按此顺序清理+插入以保证外键依赖）
         *
         * Phase 16：**15 张表**（原 13 张 - `sub_tasks` + `task_lists` + `event_categories` + `student_profile`）
         *
         * - 删 `sub_tasks`：Phase 16 把「清单项」并进 `tasks`，子项改由 `tasks.parentTaskId` 自引用表达
         * - 加 `task_lists`：任务的归属清单（`tasks.listId` → `task_lists.id`）
         * - 加 `event_categories`：日历事件分类（`events.categoryId` 指向它）
         * - 加 `student_profile`：「我」档案（一账户一行，主键 accountId）
         *
         * Phase 23：**17 张表**（+ `habits` + `habit_checkins`）
         * - 加 `habits`：习惯本体
         * - 加 `habit_checkins`：习惯打卡记录（`habitId` → `habits.id`，CASCADE）
         *
         * 排序规则：**父表在前、子表在后** —— 本顺序同时决定三件事：
         * ① **导出顺序** —— [com.example.personalassistant.core.backup.exporter.BackupExporter]
         *    按本顺序逐张写 ZIP，父表先写，读回来时顺序才对得上；
         * ② **导入插入顺序** —— 按本顺序**正序**插入，被依赖的父行必须先落库；
         * ③ **导入清表顺序** —— 按本顺序的**反序**执行（先删子表、再删父表）。
         *
         * ⚠️ 改这个列表 = 同时改了上面三件事的顺序，外键依赖顺序要重新核一遍。
         *
         * - `user_profiles` 排最前 —— 记账 4 表与 `student_profile` 的 accountId 指向它
         * - `task_lists` 排在 `tasks` 前 —— `tasks.listId` → `task_lists.id`（CASCADE）
         * - `task_tag_cross_ref` 排在 `tasks` / `tags` 后 —— (taskId, tagId) 联合外键
         * - `habits` 排在 `habit_checkins` 前 —— `habit_checkins.habitId` → `habits.id`（CASCADE）
         * - `student_profile` / `habits` / `habit_checkins` 都是叶子表，排在最后
         */
        val DEFAULT_TABLES: List<String> = listOf(
            // Phase 3：账户表排最前 —— 记账 4 表的 accountId 指向它，必须先就位
            "user_profiles",
            "semesters",
            "courses",
            "tags",
            // Phase 16：清单表 —— tasks.listId 指向它，必须排在 tasks 前
            "task_lists",
            "tasks",
            "task_tag_cross_ref",
            "events",
            // Phase 16：日历事件分类 —— events.categoryId 指向它
            "event_categories",
            "categories",
            "monthly_budgets",
            "daily_pools",
            "transactions",
            // Phase 8 Sub-step 7：节假日表（v6 新增）
            "holidays",
            // Phase 16：「我」档案 —— accountId → user_profiles，叶子表
            "student_profile",
            // Phase 23：习惯本体 —— habit_checkins.habitId 指向它，必须排在 habit_checkins 前
            "habits",
            // Phase 23：习惯打卡记录 —— 叶子表，排最后
            "habit_checkins",
        )
    }
}
