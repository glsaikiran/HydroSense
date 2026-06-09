package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_logs ORDER BY timestamp DESC")
    fun getAllWaterLogs(): Flow<List<WaterLog>>

    @Query("SELECT * FROM water_logs WHERE timestamp >= :startOfDay ORDER BY timestamp ASC")
    fun getWaterLogsSince(startOfDay: Long): Flow<List<WaterLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterLog(log: WaterLog)

    @Delete
    suspend fun deleteWaterLog(log: WaterLog)

    @Query("DELETE FROM water_logs WHERE id = :logId")
    suspend fun deleteWaterLogById(logId: Int)

    @Query("SELECT * FROM movement_logs ORDER BY timestamp DESC")
    fun getAllMovementLogs(): Flow<List<MovementLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovementLog(log: MovementLog)

    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getUserSettingsFlow(): Flow<UserSettings?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getUserSettingsDirect(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: UserSettings)
}
