package com.example.personalassistant.feature.settings.theme

import javax.inject.Inject

/**
 * 设置 Material You 动态色开关（UseCase 层）
 *
 * Phase 8 Sub-step 6 B 方案：把"写"也抽 UseCase，方便未来加埋点/校验/迁移逻辑。
 */
open class SetDynamicColorUseCase @Inject constructor(
    private val repository: ThemeRepository,
) {
    open suspend operator fun invoke(enabled: Boolean) {
        repository.setDynamicColor(enabled)
    }
}
