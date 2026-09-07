package com.example.personalassistant.feature.settings.theme

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察主题偏好（UseCase 层）
 *
 * Phase 8 Sub-step 6 B 方案：把"读 Flow"单独抽出 UseCase，
 * 方便 ViewModel 只依赖这一小段、不需要直接 inject Repository。
 *
 * 注：标注 [open] 是为了让测试 FakeUseCase 可以继承并 override [invoke]。
 */
open class ObserveThemePreferencesUseCase @Inject constructor(
    private val repository: ThemeRepository,
) {
    open operator fun invoke(): Flow<ThemePreferences> = repository.observe()
}
