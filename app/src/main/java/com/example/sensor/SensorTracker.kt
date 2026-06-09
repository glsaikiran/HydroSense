package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

class SensorTracker(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Real-time sensor states
    private val _accelerometerValues = MutableStateFlow(Triple(0f, 0f, 0f))
    val accelerometerValues: StateFlow<Triple<Float, Float, Float>> = _accelerometerValues

    private val _gyroscopeValues = MutableStateFlow(Triple(0f, 0f, 0f))
    val gyroscopeValues: StateFlow<Triple<Float, Float, Float>> = _gyroscopeValues

    private val _motionIntensity = MutableStateFlow(0f)
    val motionIntensity: StateFlow<Float> = _motionIntensity

    private val _activityLevel = MutableStateFlow("Low") // "Low", "Medium", "High"
    val activityLevel: StateFlow<String> = _activityLevel

    // Support simulated mode for non-sensor environments
    private val _isSimulatedMode = MutableStateFlow(false)
    val isSimulatedMode: StateFlow<Boolean> = _isSimulatedMode

    private var accelListener: SensorEventListener? = null
    private var gyroListener: SensorEventListener? = null

    // For activity estimation (smoothed motion score over time)
    private var smoothedIntensity = 0f
    private val ALPHA = 0.15f // Low pass filter factor

    fun startTracking() {
        if (_isSimulatedMode.value) return

        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || _isSimulatedMode.value) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                _accelerometerValues.value = Triple(x, y, z)

                // Calculate motion intensity (acceleration magnitude minus gravity)
                val magnitude = sqrt(x * x + y * y + z * z)
                val delta = abs(magnitude - 9.81f)
                processNewIntensity(delta)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        gyroListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || _isSimulatedMode.value) return
                val wx = event.values[0]
                val wy = event.values[1]
                val wz = event.values[2]
                _gyroscopeValues.value = Triple(wx, wy, wz)

                // Add rotation velocity to intensity calculation
                val rotationSpeed = sqrt(wx * wx + wy * wy + wz * wz)
                // Gyroscope intensity is weighted and added
                processNewIntensity(rotationSpeed * 1.5f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelSensor?.let {
            sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroSensor?.let {
            sensorManager.registerListener(gyroListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopTracking() {
        accelListener?.let { sensorManager.unregisterListener(it) }
        gyroListener?.let { sensorManager.unregisterListener(it) }
    }

    private fun processNewIntensity(newVal: Float) {
        // Smooth intensity to filter out noise
        smoothedIntensity = ALPHA * newVal + (1 - ALPHA) * smoothedIntensity
        _motionIntensity.value = smoothedIntensity

        // Determine activity level based on intensity thresholds
        _activityLevel.value = when {
            smoothedIntensity > 6.0f -> "High"      // Intense movement (running, shaking)
            smoothedIntensity > 1.2f -> "Medium"    // General movement (walking, carrying)
            else -> "Low"                           // Stationary (sitting, resting on table)
        }
    }

    // Interactive controls to simulate different states (crucial for demo and test evaluation)
    fun enableSimulation(enabled: Boolean) {
        _isSimulatedMode.value = enabled
        if (enabled) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    fun simulateState(state: String) {
        if (!_isSimulatedMode.value) return
        _activityLevel.value = state
        when (state) {
            "Low" -> {
                _motionIntensity.value = 0.05f
                _accelerometerValues.value = Triple(0f, 0f, 9.8f)
                _gyroscopeValues.value = Triple(0f, 0f, 0f)
            }
            "Medium" -> {
                _motionIntensity.value = 2.5f
                _accelerometerValues.value = Triple(1.5f, -2f, 10.2f)
                _gyroscopeValues.value = Triple(0.5f, 0.2f, -0.4f)
            }
            "High" -> {
                _motionIntensity.value = 8.5f
                _accelerometerValues.value = Triple(-4f, 6.5f, 12.8f)
                _gyroscopeValues.value = Triple(2.8f, -1.5f, 3.2f)
            }
        }
    }
}
