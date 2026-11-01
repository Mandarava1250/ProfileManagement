package com.example.personalassistant.core.holiday.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.core.holiday.model.HolidayType
import kotlinx.coroutines.flow.Flow

/**
 * Phase 8 Sub-step 7：节假日 DAO
 *
 * 暴露给 Repository 层的最小查询集合。
 */
@Dao
interface HolidayDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(holidays: List<HolidayEntity>): List<Long>

    @Query("SELECT * FROM holidays WHERE dateEpochDay = :dateEpochDay")
    suspend fun getByDate(dateEpochDay: Long): List<HolidayEntity>

    @Query("SELECT * FROM holidays WHERE dateEpochDay = :dateEpochDay AND type = :type LIMIT 1")
    suspend fun findByDateAndType(dateEpochDay: Long, type: HolidayType): HolidayEntity?

    @Query("SELECT * FROM holidays WHERE dateEpochDay BETWEEN :startDay AND :endDay ORDER BY dateEpochDay")
    suspend fun getInRange(startDay: Long, endDay: Long): List<HolidayEntity>

    @Query("SELECT * FROM holidays WHERE dateEpochDay BETWEEN :startDay AND :endDay ORDER BY dateEpochDay")
    fun observeInRange(startDay: Long, endDay: Long): Flow<List<HolidayEntity>>

    @Query("SELECT MAX(year) FROM holidays")
    suspend fun getMaxYear(): Int?

    @Query("SELECT MIN(year) FROM holidays")
    suspend fun getMinYear(): Int?

    @Query("SELECT COUNT(*) FROM holidays")
    suspend fun count(): Int

    /**
     * Phase 8 Sub-step 7 备份导入：清空 holidays 表。
     */
    @Query("DELETE FROM holidays")
    suspend fun clearAll(): Int

    /**
     * Phase 8 Sub-step 4 备份导出：整表快照。
     */
    @Query("SELECT * FROM holidays")
    suspend fun getAll(): List<HolidayEntity>
}