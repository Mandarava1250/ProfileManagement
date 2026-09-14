package com.example.personalassistant.feature.calendar.di

import com.example.personalassistant.core.lunar.DefaultLunarCalendar
import com.example.personalassistant.core.lunar.LunarCalendar
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 9 Step 5：农历日历的 Hilt 绑定
 *
 * - [DefaultLunarCalendar] 已通过 @Inject constructor + @Singleton 自标注，
 *   这里只需把接口绑到实现
 * - 单测里可构造 FakeLunarCalendar 实现，绕过 lunar-java 做断言
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LunarModule {

    @Binds
    @Singleton
    abstract fun bindLunarCalendar(impl: DefaultLunarCalendar): LunarCalendar
}