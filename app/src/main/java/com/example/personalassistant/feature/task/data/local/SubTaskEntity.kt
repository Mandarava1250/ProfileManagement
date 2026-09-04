package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 子任务实体（任务清单项）
 *
 * 与 TaskEntity 是 1:N 关系，parentTaskId 为 0 时表示顶级清单项
 * （也支持无 Task 的纯清单模式）
 */
@Entity(
    tableName = "sub_tasks",
    indices = [
        Index("parentTaskId"),
        Index(value = ["parentTaskId", "order"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SubTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "parentTaskId")
    val parentTaskId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "isCompleted")
    val isCompleted: Boolean = false,

    /** 显示顺序 */
    @ColumnInfo(name = "order")
    val order: Int = 0,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
