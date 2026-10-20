package com.example.personalassistant.feature.me.data.repository

import com.example.personalassistant.feature.me.data.local.StudentProfileDao
import com.example.personalassistant.feature.me.data.mapper.toDomain
import com.example.personalassistant.feature.me.data.mapper.toEntity
import com.example.personalassistant.feature.me.domain.model.StudentProfile
import com.example.personalassistant.feature.me.domain.repository.StudentProfileRepository
import com.example.personalassistant.feature.profile.data.local.AccountIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StudentProfileRepositoryImpl @Inject constructor(
    private val dao: StudentProfileDao,
) : StudentProfileRepository {

    override fun observeProfile(accountId: Long): Flow<StudentProfile?> =
        dao.observe(accountId).map { it?.toDomain() }

    override suspend fun getProfile(accountId: Long): StudentProfile? =
        dao.get(accountId)?.toDomain()

    /**
     * upsert 时若不存在，自动用 [AccountIds.DEFAULT_ACCOUNT_ID] 初始化一条
     * 默认记录（目标 GPA 4.0、空简历）。
     */
    override suspend fun upsertProfile(profile: StudentProfile) {
        val current = dao.get(profile.accountId)
        if (current == null) {
            dao.upsert(
                StudentProfile(
                    accountId = profile.accountId,
                    gpaTarget = profile.gpaTarget,
                    resumeText = profile.resumeText,
                    updatedAt = profile.updatedAt,
                ).toEntity()
            )
        } else {
            dao.upsert(profile.toEntity())
        }
    }
}
