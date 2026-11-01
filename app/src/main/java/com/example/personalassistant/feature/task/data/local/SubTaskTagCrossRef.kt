package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * Phase 15：清单项 ↔ 标签 多对多关联表
 *
 * - (subTaskId, tagId) 联合主键，避免重复关联
 * - 外键约束：
 *   - sub_tasks 删除：CASCADE → 关联行自动删除（清单项删了，标签关联没意义）
 *   - tags 删除：CASCADE → 关联行自动删除（标签删除语义就是「同时清关联」）
 *
 * 与 [TaskTagCrossRef]（任务 ↔ 标签）结构完全对称，只是挂在清单项上。
 */
@Entity(
    tableName = "sub_task_tag_cross_ref",
    primaryKeys = ["subTaskId", "tagId"],
    indices = [
        Index("tagId"), // 反向查询（按 tag 找清单项）
    ],
    foreignKeys = [
        ForeignKey(
            entity = SubTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["subTaskId"],
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
data class SubTaskTagCrossRef(
    @ColumnInfo(name = "subTaskId")
    val subTaskId: Long,

    @ColumnInfo(name = "tagId")
    val tagId: Long,
)
