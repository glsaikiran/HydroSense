package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class WaterRepository(private val waterDao: WaterDao) {

    val allWaterLogs: Flow<List<WaterLog>> = waterDao.getAllWaterLogs()

    val allMovementLogs: Flow<List<MovementLog>> = waterDao.getAllMovementLogs()

    val userSettings: Flow<UserSettings> = waterDao.getUserSettingsFlow().map { it ?: UserSettings() }

    fun getWaterLogsForToday(): Flow<List<WaterLog>> {
        val startOfDay = getStartOfDayTimestamp()
        return waterDao.getWaterLogsSince(startOfDay)
    }

    suspend fun getSettingsDirect(): UserSettings {
        return waterDao.getUserSettingsDirect() ?: UserSettings()
    }

    suspend fun logWater(amountMl: Int, activityLevel: String) {
        val log = WaterLog(amountMl = amountMl, activityLevel = activityLevel)
        waterDao.insertWaterLog(log)
    }

    suspend fun deleteWaterLog(log: WaterLog) {
        waterDao.deleteWaterLog(log)
    }

    suspend fun deleteWaterLogById(logId: Int) {
        waterDao.deleteWaterLogById(logId)
    }

    suspend fun logMovement(durationSeconds: Int = 120) {
        val log = MovementLog(durationSeconds = durationSeconds)
        waterDao.insertMovementLog(log)
    }

    suspend fun saveSettings(settings: UserSettings) {
        waterDao.insertOrUpdateSettings(settings)
    }

    private fun getStartOfDayTimestamp(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
