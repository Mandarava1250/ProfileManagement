package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Phase 6c：标签实体（全局共享标签库）
 *
 * - nameNormalized：小写规范化名 + UNIQUE 索引，保证大小写不敏感去重
 * - displayName：原始大小写名（UI 显示用）
 * - colorHex：色板索引的颜色（[com.example.personalassistant.feature.task.presentation.util.DefaultTagPalette.hex]）
 * - colorSource：调色板来源标识，默认 "default"。未来 v扩展User 可扩展为 "user:<paletteId>" 等命名空间
 * - createdAt：标签创建时间
 *
 * 设计取舍：
 * - 同名字符串靠 DB UNIQUE 约束 + nameNormalized 小写化做去重（Repository 层面 fetch-or-create）
 * - colorSource 字段提前预留，避免 v6 升级破坏 schema
 */
@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["nameNormalized"], unique = true),
        Index("colorSource"),
    ]
)
@Serializable
data class TagEntity(
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
    val colorHex: Long,

    /**
     * 调色板来源标识。
     *
     * - "default"：使用 [com.example.personalassistant.feature.task.presentation.util.DefaultTagPalette]
     * - 未来可扩展：比如 "user:customA" 表示用户自定义色板 A
     *
     * 字段已预留，方便未来扩展无 schema 破坏。
     */
    @ColumnInfo(name = "colorSource", defaultValue = "default")
    val colorSource: String = "default",

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
)