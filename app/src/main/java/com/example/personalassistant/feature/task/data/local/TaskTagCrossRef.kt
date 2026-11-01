package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * Phase 6c：任务 ↔ 标签 多对多关联表
 *
 * - (taskId, tagId) 联合主键，避免重复关联
 * - 外键约束：任务/标签被删时关联自动级联清理
 *   - tasks 删除：CASCADE → 关联行自动删除
 *   - tags 删除：CASCADE → 关联行自动删除（标签删除语义就是「同时清关联」，plan v3.3 决策）
 *
 * 设计取舍：使用 Junction 表而非在 tasks 上存 JSON / 多值字段，
 * 是因为 Room 对 junction 表有原生索引支持，未来查询「某 tag 下所有 task」单 SQL 即可。
 */
@Entity(
    tableName = "task_tag_cross_ref",
    primaryKeys = ["taskId", "tagId"],
    indices = [
        Index("tagId"), // 反向查询（按 tag 找 task）
    ],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        ),
    ]
)
@Serializable
data class TaskTagCrossRef(
    @ColumnInfo(name = "taskId")
    val taskId: Long,

    @ColumnInfo(name = "tagId")
    val tagId: Long,
)