package com.example.personalassistant.feature.me.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentProfileDao {
    @Query("SELECT * FROM student_profile WHERE accountId = :accountId")
    fun observe(accountId: Long): Flow<StudentProfileEntity?>

    @Query("SELECT * FROM student_profile WHERE accountId = :accountId")
    suspend fun get(accountId: Long): StudentProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StudentProfileEntity)
}
