package com.example.personalassistant.core.coroutine

import kotlinx.coroutines.Dispatchers

/**
 * 生产环境默认实现：直接代理到 kotlinx.coroutines.Dispatchers。
 */
class DefaultAppDispatchers : AppDispatchers {
    override val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
    override val default: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
    override val main: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main
}
