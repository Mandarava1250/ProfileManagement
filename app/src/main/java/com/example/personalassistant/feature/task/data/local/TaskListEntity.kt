package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Phase 16：清单实体（任务归属地）
 *
 * ## 为什么要有这一层
 *
 * Phase 15 之前，「任务」和「清单项」是两套并行的东西：
 * - `tasks` 表：有提醒 / 重复 / 描述，但没金额、没独立清单归属
 * - `sub_tasks` 表：有分类 / 金额 / 嵌套，但没提醒、没重复
 *
 * 滴答清单（TickTick）的模型是 **清单 → 任务 → 子项** 三层，
 * 清单是任务的**归属地**，标签是跨清单的**属性**，过滤器是**视图**。
 * 这一层一加，Phase 16 把两套模型合成一套，Phase 17 才能做出
 * 收集箱 / 今天 / 最近 7 天这些智能清单。
 *
 * ## 字段
 *
 * - nameNormalized：小写规范化名 + UNIQUE 索引，保证大小写不敏感去重（与 [TagEntity] 同款做法）
 * - displayName：原始大小写名（UI 显示用）
 * - colorHex：ARGB hex 颜色值（不透明色），如 0xFFFF69B4
 * - iconKey：图标标识（Material icon 的 key，缺省用默认图标）
 * - sortOrder：侧边栏排序权重（升序）
 * - isSystem：内置清单标记。`[INBOX_ID]`（收集箱）是内置的，**不可删除**
 * - isArchived：归档标记。归档的清单不出现在侧边栏，但任务还在
 * - createdAt：创建时间
 */
@Entity(
    tableName = "task_lists",
    indices = [
        Index(value = ["nameNormalized"], unique = true),
        Index("sortOrder"),
    ]
)
@Serializable
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 小写规范化名（DB UNIQUE 去重键），如 "work" */
    @ColumnInfo(name = "nameNormalized")
    val nameNormalized: String,

    /** UI 显示用的原始名（保留用户输入的大小写），如 "Work" */
    @ColumnInfo(name = "displayName")
    val displayName: String,

    /** ARGB hex 颜色值（不透明色），如 0xFFFF69B4 */
    @ColumnInfo(name = "colorHex")
    val colorHex: Long = 0xFFFF69B4L,

    /** 图标标识（Material icon key），null / 空串 = 用默认图标 */
    @ColumnInfo(name = "iconKey")
    val iconKey: String = "",

    /** 侧边栏排序权重（升序，值越小越靠前） */
    @ColumnInfo(name = "sortOrder")
    val sortOrder: Int = 0,

    /**
     * 是否内置清单。
     *
     * 内置清单不可删除 —— 收集箱是所有快速捕获的默认落点，删了整条捕获链路就断了。
     */
    @ColumnInfo(name = "isSystem")
    val isSystem: Boolean = false,

    /** 是否归档（归档后侧边栏不显示，任务仍在） */
    @ColumnInfo(name = "isArchived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /**
         * 内置「收集箱」的固定 id。
         *
         * 固定成 1 是为了让 `tasks.listId` 能在 v15 迁移里用
         * `NOT NULL DEFAULT 1` 一次性补齐存量行 —— 所有老任务先落收集箱，
         * 用户再自己分流。
         */
        const val INBOX_ID = 1L

        /** 内置收集箱的规范化名 */
        const val INBOX_NAME_NORMALIZED = "inbox"

        /** 内置收集箱的显示名 */
        const val INBOX_DISPLAY_NAME = "收集箱"
    }
}
