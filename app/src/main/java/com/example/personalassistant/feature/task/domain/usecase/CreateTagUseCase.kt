package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.repository.TagRepository
import javax.inject.Inject

/**
 * Phase 6c：创建一个新标签（或获取已存在的同 normalized name 的标签）。
 *
 * - displayName：用户输入的原样名（trim 后作为 displayName 落库）
 * - colorHex：色板选出的 ARGB hex 颜色值
 * - 返回：去重后的 Tag（已存在则返回已存在的，新创建则返回新创建的）
 *
 * 内部委托给 [TagRepository.createTag]，业务校验（trim、非空）也在 Repository 层抛 IllegalArgumentException。
 */
open class CreateTagUseCase @Inject constructor(
    private val repository: TagRepository,
) {
    open suspend operator fun invoke(displayName: String, colorHex: Long): Tag =
        repository.createTag(displayName, colorHex)
}