package com.example.personalassistant.feature.me.domain.repository

import com.example.personalassistant.feature.me.domain.model.StudentProfile
import kotlinx.coroutines.flow.Flow

interface StudentProfileRepository {
    fun observeProfile(accountId: Long): Flow<StudentProfile?>
    suspend fun getProfile(accountId: Long): StudentProfile?
    suspend fun upsertProfile(profile: StudentProfile)
}
