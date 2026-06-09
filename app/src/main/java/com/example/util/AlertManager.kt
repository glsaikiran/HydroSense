package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import android.util.Log

object AlertManager {
    fun playSoundAndVibrate(
        context: Context,
        enableSound: Boolean,
        soundType: String,
        enableVibration: Boolean,
        vibrationType: String
    ) {
        if (enableSound) {
            triggerSound(soundType)
        }
        if (enableVibration) {
            triggerVibration(context, vibrationType)
        }
    }

    fun triggerSound(soundType: String) {
        try {
            val toneType = when (soundType) {
                "Ripple" -> ToneGenerator.TONE_SUP_CONFIRM
                "Bubble" -> ToneGenerator.TONE_CDMA_PIP
                "Ding" -> ToneGenerator.TONE_PROP_PROMPT
                "Gong" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95)
            toneGen.startTone(toneType, 350) // play for 350 ms
            // Release after delay to prevent leaking memory
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    toneGen.release()
                } catch (ignored: Exception) {}
            }, 1000)
        } catch (e: Exception) {
            Log.e("AlertManager", "Error playing notification audio tone", e)
        }
    }

    fun triggerVibration(context: Context, vibrationType: String) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                val timings = when (vibrationType) {
                    "Classic" -> longArrayOf(0, 450) // Vibrate 450ms
                    "Heartbeat" -> longArrayOf(0, 180, 180, 180) // double heartbeat tap
                    "Pulse" -> longArrayOf(0, 120, 120, 120, 120, 120) // pulse succession
                    "SOS" -> longArrayOf(
                        0, 150, 150, 150, 150, 150, // S: . . .
                        250, 400, 150, 400, 150, 400, // O: - - -
                        250, 150, 150, 150, 150, 150  // S: . . .
                    )
                    else -> longArrayOf(0, 300)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, -1)
                }
            }
        } catch (e: Exception) {
            Log.e("AlertManager", "Error triggering vibration", e)
        }
    }
}
