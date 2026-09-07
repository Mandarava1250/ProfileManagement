package com.example.personalassistant.feature.settings.theme

import javax.inject.Inject

/**
 * 设置主题模式（UseCase 层）
 *
 * Phase 8 Sub-step 6 B 方案：把"写"也抽 UseCase，方便未来加埋点/校验/迁移逻辑。
 */
open class SetThemeModeUseCase @Inject constructor(
    private val repository: ThemeRepository,
) {
    open suspend operator fun invoke(mode: ThemeMode) {
        repository.setMode(mode)
    }
}
