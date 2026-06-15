package com.example.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.WaterDatabase
import com.example.data.WaterRepository
import com.example.util.AlertManager
import com.example.data.UserSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.Calendar

class SedentaryService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: WaterRepository
    private lateinit var sensorTracker: SensorTracker
    private var serviceJob: Job? = null

    private var lastActiveTime: Long = 0L
    private var hasAlerted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        val db = WaterDatabase.getDatabase(this)
        repository = WaterRepository(db.waterDao())
        sensorTracker = SensorTracker(this)
        sensorTracker.startTracking()

        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        startSedentaryCheckLoop()
        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Foreground Service Channel (Low importance so it doesn't make noisy buzzes constantly)
            val fgChannel = NotificationChannel(
                CHANNEL_FOREGROUND_ID,
                "Activity Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the sedentary activity sensor active in the background."
                setShowBadge(false)
            }
            manager.createNotificationChannel(fgChannel)

            // Alert Channel (High importance for alerts, heads up display)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Sedentary Reminder Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers alarm tones, vibrations, and banners when you are inactive too long."
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun startForegroundServiceNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_FOREGROUND_ID)
            .setContentTitle("Sedentary Monitor Active")
            .setContentText("Hydrosense is running background activity sensors.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_FG_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_FG_ID, notification)
        }
    }

    private fun startSedentaryCheckLoop() {
        serviceJob?.cancel()
        serviceJob = serviceScope.launch {
            // First, constantly observe userSettings Flow to get latest lastActiveTime, bedtime, etc.
            launch {
                repository.userSettings.collectLatest { settings ->
                    if (settings.lastActiveTimestamp != 0L) {
                        if (lastActiveTime != settings.lastActiveTimestamp) {
                            lastActiveTime = settings.lastActiveTimestamp
                            // Reset the alerted state because a new active timestamp indicates either:
                            // 1. User stood up/moved (resetting state).
                            // 2. User manually reset/dismissed the alert.
                            hasAlerted = false
                        }
                    }
                }
            }

            // Loop checking every second
            var movementAccumulator = 0
            while (true) {
                delay(1000)

                if (lastActiveTime == 0L) {
                    continue
                }

                val dbSettings = repository.getSettingsDirect()
                // Fetch dynamic activity level
                val activity = if (dbSettings.selectedActivityProfile == "Auto") {
                    sensorTracker.activityLevel.value
                } else {
                    dbSettings.selectedActivityProfile
                }

                val elapsedSeconds = ((System.currentTimeMillis() - lastActiveTime) / 1000).toInt().coerceAtLeast(0)

                // Detect movement and update database
                if (activity != "Low") {
                    movementAccumulator = (movementAccumulator + 2).coerceAtMost(8)
                    if (movementAccumulator >= 8) {
                        resetStationaryTimerInDb()
                        movementAccumulator = 0
                    }
                } else {
                    movementAccumulator = (movementAccumulator - 1).coerceAtLeast(0)

                    // Check if elapsed limit duration is met
                    val limitSeconds = dbSettings.movementBreakIntervalMinutes * 60
                    if (elapsedSeconds >= limitSeconds && !hasAlerted) {
                        if (!isCurrentlyBedtime(dbSettings)) {
                            hasAlerted = true
                            triggerBackgroundAlert(dbSettings)
                        }
                    }
                }
            }
        }
    }

    private suspend fun resetStationaryTimerInDb() {
        val now = System.currentTimeMillis()
        lastActiveTime = now
        hasAlerted = false
        val dbSettings = repository.getSettingsDirect()
        val updated = dbSettings.copy(lastActiveTimestamp = now)
        repository.saveSettings(updated)
    }

    private fun triggerBackgroundAlert(settings: UserSettings) {
        // Trigger media (sound and vibration) using our AlertManager helper!
        AlertManager.playSoundAndVibrate(
            context = this,
            enableSound = settings.enableSound,
            soundType = settings.soundType,
            enableVibration = settings.enableVibration,
            vibrationType = settings.vibrationType
        )

        // Post visual heads up Notification which wakes screen and is highly visible on lock screen
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle("Sedentary Alert!")
            .setContentText("You've been sitting for ${settings.movementBreakIntervalMinutes} minutes. Stand up and do a quick 2-minute stretch!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // Heads up mode when locked or using other apps

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ALERT_ID, builder.build())
    }

    private fun isCurrentlyBedtime(settings: UserSettings): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val currentMinutes = hour * 60 + minute
        val startMinutes = settings.bedtimeStartHour * 60 + settings.bedtimeStartMinute
        val endMinutes = settings.bedtimeEndHour * 60 + settings.bedtimeEndMinute

        return if (startMinutes == endMinutes) {
            false
        } else if (startMinutes < endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorTracker.stopTracking()
        serviceJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_FOREGROUND_ID = "sedentary_fg_channel"
        private const val CHANNEL_ALERT_ID = "sedentary_alert_channel"
        private const val NOTIFICATION_FG_ID = 1001
        private const val NOTIFICATION_ALERT_ID = 1002
    }
}
