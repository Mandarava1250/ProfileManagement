package com.example.personalassistant.feature.settings.theme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Phase 8 Sub-step 6：ThemeViewModel 单元测试
 *
 * 策略：
 * - 用 FakeObserveThemePreferencesUseCase 控制 emit 的值
 * - 不依赖 DataStore / Hilt
 * - 用 [Dispatchers.setMain] + UnconfinedTestDispatcher 解决 viewModelScope.launch
 *   在 JVM 单测里找不到 Main dispatcher 的问题（ThemeViewModel 用 stateIn + Eagerly）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    /**
     * 假 UseCase：构造时给一个种子值，后续可手动 mutate
     */
    private class FakeObserveThemePreferencesUseCase(
        initial: ThemePreferences,
    ) : ObserveThemePreferencesUseCase(NoopRepository) {
        val upstream = MutableStateFlow(initial)
        override operator fun invoke() = upstream
    }

    @Before
    fun setUp() {
        // ThemeViewModel 构造里 stateIn(Eagerly) 会在 viewModelScope 里 launch，
        // 而 viewModelScope 默认绑定 Dispatchers.Main.immediate，JVM 单测没有这个调度器。
        // 用 UnconfinedTestDispatcher 替换，让 launch 立即在当前线程执行。
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初始值正确传递`() = runBlocking {
        val fake = FakeObserveThemePreferencesUseCase(
            ThemePreferences(ThemeMode.DARK, dynamicColor = false)
        )
        val vm = ThemeViewModel(fake, NoopRepository)

        // stateIn 用 Eagerly 启动会立即收到第一个值
        val prefs = vm.themePreferences.first { it.themeMode == ThemeMode.DARK }
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertEquals(false, prefs.dynamicColor)
    }

    @Test
    fun `默认值是 SYSTEM + dynamicColor=false`() = runBlocking {
        val fake = FakeObserveThemePreferencesUseCase(ThemePreferences())
        val vm = ThemeViewModel(fake, NoopRepository)

        val prefs = vm.themePreferences.value
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
        assertEquals(false, prefs.dynamicColor)
    }

    @Test
    fun `upstream emit 新值后 StateFlow 也跟随更新`() = runBlocking {
        val fake = FakeObserveThemePreferencesUseCase(ThemePreferences())
        val vm = ThemeViewModel(fake, NoopRepository)

        // 触发初始订阅
        vm.themePreferences.value

        // 改变上游
        fake.upstream.value = ThemePreferences(ThemeMode.LIGHT, dynamicColor = false)

        val prefs = vm.themePreferences.first { it.themeMode == ThemeMode.LIGHT }
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)
        assertEquals(false, prefs.dynamicColor)
    }

    companion object {
        /**
         * 不会被调用的假 Repository（仅用于满足 UseCase 构造器参数）
         */
        private val NoopRepository: ThemeRepository = object : ThemeRepository {
            override fun observe() = MutableStateFlow(ThemePreferences())
            override suspend fun setMode(mode: ThemeMode) = Unit
            override suspend fun setDynamicColor(enabled: Boolean) = Unit
            override suspend fun setPrimaryColor(color: Int?) = Unit
            override suspend fun setBackgroundStyle(style: BackgroundStyle) = Unit
        }
    }
}
