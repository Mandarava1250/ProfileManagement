package com.example.personalassistant.core.coroutine

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 协程调度器抽象 —— 让业务代码不直接依赖 Dispatchers.IO / Default / Main，
 * 方便在测试里注入 TestDispatcher。
 */
interface AppDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}
