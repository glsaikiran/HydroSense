package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.UserSettings
import com.example.data.WaterDatabase
import com.example.data.WaterLog
import com.example.data.WaterRepository
import com.example.sensor.SensorTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class WaterViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WaterDatabase.getDatabase(application)
    private val repository = WaterRepository(db.waterDao())
    val sensorTracker = SensorTracker(application)

    // Flow states from database
    val settingsState: StateFlow<UserSettings> = repository.userSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val todayWaterLogs: StateFlow<List<WaterLog>> = repository.getWaterLogsForToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWaterLogs: StateFlow<List<WaterLog>> = repository.allWaterLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movementLogs: StateFlow<List<com.example.data.MovementLog>> = repository.allMovementLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Interactive States
    private val _currentWaterProgress = MutableStateFlow(0f)
    val currentWaterProgress: StateFlow<Float> = _currentWaterProgress

    private val _totalTodayIntake = MutableStateFlow(0)
    val totalTodayIntake: StateFlow<Int> = _totalTodayIntake

    // Dynamic hydration reminder interval calculation (in minutes)
    private val _nextHydrationIntervalMinutes = MutableStateFlow(90)
    val nextHydrationIntervalMinutes: StateFlow<Int> = _nextHydrationIntervalMinutes

    // Interactive sensor activity tracking (Low, Medium, High)
    val currentActivityLevel: StateFlow<String> = sensorTracker.activityLevel
    val sensorMotionIntensity: StateFlow<Float> = sensorTracker.motionIntensity

    // Stationary tracking states (Continuous Sitting / Inactivity timer)
    private val _stationarySeconds = MutableStateFlow(0)
    val stationarySeconds: StateFlow<Int> = _stationarySeconds

    // Movement threshold accumulator to filter out sudden picking up or viewing phone.
    // Represents progress (0.0 to 1.0) towards performing active stand up / walk.
    private val _movementAccumulator = MutableStateFlow(0f)
    val movementAccumulator: StateFlow<Float> = _movementAccumulator

    private val _showBreakAlert = MutableStateFlow(false)
    val showBreakAlert: StateFlow<Boolean> = _showBreakAlert

    // Active break details (For the 2 minutes stretch break timer)
    private val _activeBreakTimerSeconds = MutableStateFlow(0)
    val activeBreakTimerSeconds: StateFlow<Int> = _activeBreakTimerSeconds
    private val _isBreakTimerActive = MutableStateFlow(false)
    val isBreakTimerActive: StateFlow<Boolean> = _isBreakTimerActive

    // Timer Job for counting seconds
    private var tickerJob: Job? = null
    // Timer Job for active stretch break
    private var breakTimerJob: Job? = null

    init {
        // Start sensor hardware tracking
        sensorTracker.startTracking()

        // Sync calculation of progress whenever logs or settings change
        viewModelScope.launch {
            combine(todayWaterLogs, settingsState) { logs, settings ->
                val total = logs.sumOf { it.amountMl }
                _totalTodayIntake.value = total
                val progress = if (settings.dailyTargetMl > 0) {
                    total.toFloat() / settings.dailyTargetMl
                } else {
                    0f
                }
                _currentWaterProgress.value = progress.coerceIn(0f, 1f)

                // Mathematically calculate reminder interval:
                // Wake hours = 14 hrs = 840 mins
                // Servings needed = Daily Target / Serving Size (specified by "waterQuantityLevelMl")
                val target = settings.dailyTargetMl.toFloat()
                val servingSize = settings.waterQuantityLevelMl.toFloat()
                val servingsNeeded = if (servingSize > 0) target / servingSize else 8f
                
                // Base interval in minutes
                val baseInterval = if (servingsNeeded > 0) 840 / servingsNeeded else 105f
                
                // Adjust interval based on sensor activity levels mapping
                val currentActivity = if (settings.selectedActivityProfile == "Auto") {
                    sensorTracker.activityLevel.value
                } else {
                    settings.selectedActivityProfile
                }

                val finalInterval = when (currentActivity) {
                    "High" -> baseInterval * 0.70f // Shorten by 30% (need to drink water more frequently)
                    "Medium" -> baseInterval * 0.85f // Shorten by 15%
                    else -> baseInterval // Low activity/Standard
                }
                
                _nextHydrationIntervalMinutes.value = finalInterval.toInt().coerceIn(10, 360)
            }.collect()
        }

        // Ticker to track background time elapsed, stationary time, etc.
        startInappInactivityTicker()
    }

    private fun startInappInactivityTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var currentAccumulator = 0
            while (true) {
                delay(1000) // tick every second

                val settings = settingsState.value
                val activity = if (settings.selectedActivityProfile == "Auto") {
                    sensorTracker.activityLevel.value
                } else {
                    settings.selectedActivityProfile
                }

                // Only reset sedentary duration when a threshold of sustained movement is met (e.g. accumulator reaches 8)
                // Short movements like lifting the phone to view it will only build 2-4 units and then decay without resetting.
                if (activity != "Low") {
                    currentAccumulator = (currentAccumulator + 2).coerceAtMost(8)
                    _movementAccumulator.value = currentAccumulator.toFloat() / 8f
                    
                    if (currentAccumulator >= 8) {
                        _stationarySeconds.value = 0
                    }
                } else {
                    currentAccumulator = (currentAccumulator - 1).coerceAtLeast(0)
                    _movementAccumulator.value = currentAccumulator.toFloat() / 8f

                    _stationarySeconds.value += 1

                    // Check if they hit the movement break interval
                    val limitSeconds = settings.movementBreakIntervalMinutes * 60
                    if (_stationarySeconds.value >= limitSeconds && !_showBreakAlert.value) {
                        _showBreakAlert.value = true
                    }
                }
            }
        }
    }

    // Manual triggers
    fun logWaterServing() {
        viewModelScope.launch {
            val settings = settingsState.value
            val currentActivity = if (settings.selectedActivityProfile == "Auto") {
                sensorTracker.activityLevel.value
            } else {
                settings.selectedActivityProfile
            }
            repository.logWater(
                amountMl = settings.waterQuantityLevelMl,
                activityLevel = currentActivity
            )
        }
    }

    fun logCustomWater(amountMl: Int) {
        viewModelScope.launch {
            val settings = settingsState.value
            val currentActivity = if (settings.selectedActivityProfile == "Auto") {
                sensorTracker.activityLevel.value
            } else {
                settings.selectedActivityProfile
            }
            repository.logWater(
                amountMl = amountMl,
                activityLevel = currentActivity
            )
        }
    }

    fun deleteWaterLog(log: WaterLog) {
        viewModelScope.launch {
            repository.deleteWaterLog(log)
        }
    }

    fun saveUserSettings(
        dailyTargetMl: Int,
        waterQuantityLevelMl: Int,
        breakMinutes: Int,
        sensorEnabled: Boolean,
        activityProfile: String
    ) {
        viewModelScope.launch {
            val updated = UserSettings(
                dailyTargetMl = dailyTargetMl,
                waterQuantityLevelMl = waterQuantityLevelMl,
                movementBreakIntervalMinutes = breakMinutes,
                enableSensorTracking = sensorEnabled,
                selectedActivityProfile = activityProfile
            )
            repository.saveSettings(updated)
            sensorTracker.enableSimulation(activityProfile != "Auto")
            if (activityProfile != "Auto") {
                sensorTracker.simulateState(activityProfile)
            }
        }
    }

    fun dismissBreakAlert() {
        _showBreakAlert.value = false
        _stationarySeconds.value = 0 // reset stationary counter
    }

    // Launch stretch/break dynamic timer
    fun startStretchBreakTimer() {
        _showBreakAlert.value = false
        _activeBreakTimerSeconds.value = 120 // 2 minutes (120 seconds) best practice break duration
        _isBreakTimerActive.value = true

        breakTimerJob?.cancel()
        breakTimerJob = viewModelScope.launch {
            while (_activeBreakTimerSeconds.value > 0) {
                delay(1000)
                _activeBreakTimerSeconds.value -= 1
            }
            // Finished!
            completeStretchBreak()
        }
    }

    fun skipStretchBreakTimer() {
        _isBreakTimerActive.value = false
        breakTimerJob?.cancel()
        _stationarySeconds.value = 0
    }

    private fun completeStretchBreak() {
        _isBreakTimerActive.value = false
        // Save completed break to database log
        viewModelScope.launch {
            repository.logMovement(durationSeconds = 120)
        }
        _stationarySeconds.value = 0
    }

    // Convenience test triggers for evaluating features easily
    fun triggerDemoBreakAlertImmediately() {
        _showBreakAlert.value = true
    }

    fun fastForwardStationaryTime(bySeconds: Int) {
        _stationarySeconds.value += bySeconds
        val limitSeconds = settingsState.value.movementBreakIntervalMinutes * 60
        if (_stationarySeconds.value >= limitSeconds) {
            _showBreakAlert.value = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorTracker.stopTracking()
        tickerJob?.cancel()
        breakTimerJob?.cancel()
    }
}
