package com.example.personalassistant.feature.profile.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Phase 3：账户 DAO
 *
 * 最小集合：观察列表 / 按 id 取 / 插入 / 更新 / 重命名 / 删除 / 计数。
 *
 * ⚠️ 这里**不**提供「当前账户」的读写 —— 当前账户 id 是设备级偏好，
 *    走 DataStore（见 [com.example.personalassistant.feature.profile.data.CurrentAccountRepository]），
 *    不进 Room 表，避免每次切账户都要写库 + 冷启动竞态。
 */
@Dao
interface UserProfileDao {

    /** 全部账户，按 id 升序（默认账户恒在最前） */
    @Query("SELECT * FROM user_profiles ORDER BY id ASC")
    fun observeAll(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles ORDER BY id ASC")
    suspend fun getAll(): List<UserProfileEntity>

    /**
     * Phase 3 备份导入：清空 user_profiles 表（与 holidays.clearAll 同构）。
     */
    @Query("DELETE FROM user_profiles")
    suspend fun clearAll(): Int

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: Long): UserProfileEntity?

    /** 账户总数（UI 用于「仅剩 1 个账户时禁删」判断） */
    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun count(): Int

    /**
     * 插入账户。id=0 时由 Room autoGenerate 分配；
     * REPLACE 让迁移 / seed 场景可以用显式 id=1 幂等重写。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity): Long

    /**
     * 批量插入账户（REPLACE，与 [insert] 一致）。
     *
     * 备份导入时账户数量很少（个位数），这里的收益主要来自**少一层循环**，
     * 但接口留着是为了让「逐行插」与「批量插」两条路径的冲突策略写在同一处，
     * 以后改策略不会只改一边。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<UserProfileEntity>): List<Long>

    @Update
    suspend fun update(profile: UserProfileEntity)

    @Delete
    suspend fun delete(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE user_profiles SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /**
     * Phase 4：更新头像 URI（传 null 即清除头像）。
     *
     * 不用 [update] 整行写回：那需要先查后写，两步之间别人改了名字就会被覆盖回去。
     * 单条 UPDATE 只动 `avatarUri` 一列，不存在这个问题。
     */
    @Query("UPDATE user_profiles SET avatarUri = :avatarUri WHERE id = :id")
    suspend fun updateAvatar(id: Long, avatarUri: String?)

    /**
     * Phase 5：单独更新期初余额。
     *
     * 为什么不用现成的 @Update(entity)：那个要先 getById 再 copy 再写回，
     * 中间窗口里若并发改了名字会被本次写回覆盖（丢字段）。写列级 UPDATE 只动这一列，
     * 与 [rename] / [updateAvatar] 同一取向。
     */
    @Query("UPDATE user_profiles SET initialBalance = :balance WHERE id = :id")
    suspend fun updateInitialBalance(id: Long, balance: Double)
}
