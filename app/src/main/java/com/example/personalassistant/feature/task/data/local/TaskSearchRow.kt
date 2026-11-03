package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * Phase 22.2：搜索命中投影（清单项 + 命中来源）
 *
 * 搜「Work」时，清单项标题里可能压根没有 Work 这两个字 ——
 * 它是因为**打了 Work 标签**或**在 Work 清单里**才被搜出来的。
 * 没有命中来源的话，用户会一脸茫然。
 */
data class TaskSearchRow(
    @Embedded
    val task: TaskEntity,

    /** 命中的标签名（多个用「 / 」连接）。null = 没靠标签命中。 */
    @ColumnInfo(name = "matchedTagNames")
    val matchedTagNames: String?,

    /** 命中的清单名。null = 没靠清单名命中。 */
    @ColumnInfo(name = "matchedListName")
    val matchedListName: String?,
)
