package com.poziomnica.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.poziomnica.settings.UserSettings

class VibrationController(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private var wasInTolerance = false

    fun update(inTolerance: Boolean, settings: UserSettings) {
        if (!settings.vibrationEnabled) {
            wasInTolerance = false
            return
        }
        if (inTolerance && !wasInTolerance) vibrate(settings)
        wasInTolerance = inTolerance
    }

    fun vibrate(settings: UserSettings) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(settings.vibrationDurationMs, settings.vibrationStrength.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(settings.vibrationDurationMs)
        }
    }
}
