package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amountMl: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val activityLevel: String // "Low", "Medium", "High"
)

@Entity(tableName = "movement_logs")
data class MovementLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isBreakTaken: Boolean = true,
    val durationSeconds: Int = 120 // 2 minutes by default
)

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1, // Single row config
    val dailyTargetMl: Int = 2500,
    val waterQuantityLevelMl: Int = 250, // 150ml, 250ml, 330ml, 500ml
    val movementBreakIntervalMinutes: Int = 50, // Best practices: 45-60 mins
    val enableSensorTracking: Boolean = true,
    val selectedActivityProfile: String = "Auto" // "Auto", "Low", "Medium", "High"
)
