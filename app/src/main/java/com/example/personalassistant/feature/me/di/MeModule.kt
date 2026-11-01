package com.example.personalassistant.feature.me.di

import com.example.personalassistant.feature.me.data.repository.StudentProfileRepositoryImpl
import com.example.personalassistant.feature.me.domain.repository.StudentProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MeModule {

    @Binds
    @Singleton
    abstract fun bindStudentProfileRepository(
        impl: StudentProfileRepositoryImpl,
    ): StudentProfileRepository
}
