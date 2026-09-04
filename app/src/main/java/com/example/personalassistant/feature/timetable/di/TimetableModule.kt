package com.example.personalassistant.feature.timetable.di

import com.example.personalassistant.feature.timetable.data.repository.TimetableRepositoryImpl
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 课表模块 Hilt 绑定
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimetableModule {

    @Binds
    @Singleton
    abstract fun bindTimetableRepository(
        impl: TimetableRepositoryImpl
    ): TimetableRepository
}