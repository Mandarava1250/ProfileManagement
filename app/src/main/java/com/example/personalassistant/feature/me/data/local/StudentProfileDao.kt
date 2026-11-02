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

    // ===== 备份导出 / 导入 =====

    /** 全量档案（备份导出用；一账户一行，主键 accountId） */
    @Query("SELECT * FROM student_profile")
    suspend fun getAllStudentProfiles(): List<StudentProfileEntity>

    /**
     * 批量写入档案（备份导入用）
     *
     * ⚠️ 冲突策略必须与 [upsert] **逐字一致**（REPLACE ↔ REPLACE）——
     * 备份恢复走「按主键覆盖」语义，批量写与单条写不能出现行为差异。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<StudentProfileEntity>)

    /** 清空档案表（备份导入的整表替换步骤用） */
    @Query("DELETE FROM student_profile")
    suspend fun clearAll(): Int
}
