package com.example.personalassistant.feature.task.domain.repository

import com.example.personalassistant.feature.task.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Phase 6c：标签领域仓库接口
 *
 * 由 Data 层的 TagRepositoryImpl 实现。
 *
 * ## 接口语义
 * - **读流**：observeXxx 返回 Flow，UI 层直接 collect
 *   - [observeAllTagsWithCount]：标签云（频次降序），用于顶部 chip 行
 *   - [observeTagsForTask]：单任务关联的标签，用于详情页 TagSection
 * - **写操作**：suspend，由 UseCase 层调用
 *   - [createTag]：去重创建（大小写不敏感），返回已存在或新创建的 Tag
 *   - [deleteTag]：删除标签，FK CASCADE 自动清掉关联行
 *   - [addTagToTask]：把标签绑到任务上
 *   - [removeTagFromTask]：从任务上卸下标签
 */
interface TagRepository {

    // ===== 读流 =====

    /** 全部标签按创建时间升序（用于标签管理面板、详细列表） */
    fun observeAllTags(): Flow<List<Tag>>

    /** 标签云：按使用频次降序，同频次按 createdAt 升序 */
    fun observeAllTagsWithCount(): Flow<List<Tag>>

    /** 某任务关联的全部标签（按 createdAt 升序） */
    fun observeTagsForTask(taskId: Long): Flow<List<Tag>>

    // ===== 写操作 =====

    /**
     * 去重创建或获取已存在的标签（按 normalized name）。
     *
     * - 输入：displayName（用户输入的原样名，如 "Work"），colorHex（从 TagPalette 选的色）
     * - 行为：trim + lowercase 得到 nameNormalized
     *   - DB 已有同名标签：返回已存在的 Tag（id, 已存在的 displayName / colorHex / colorSource）
     *   - DB 无同名标签：新建并返回
     * - 空字符串 / 纯空格 → 抛 IllegalArgumentException
     */
    suspend fun createTag(displayName: String, colorHex: Long): Tag

    /** 删除标签（FK CASCADE 自动清掉 task_tag_cross_ref 关联行） */
    suspend fun deleteTag(id: Long)

    /**
     * 给任务打标签（已存在关联则忽略）。
     *
     * 任务被删时关联由 FK CASCADE 自动清理。
     */
    suspend fun addTagToTask(taskId: Long, tagId: Long)

    /** 从任务上卸下标签 */
    suspend fun removeTagFromTask(taskId: Long, tagId: Long)
}