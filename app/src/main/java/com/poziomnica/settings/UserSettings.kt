package com.poziomnica.settings

import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.IndicatorStyle
import com.poziomnica.domain.SmoothingLevel
import com.poziomnica.domain.SoundMode

data class UserSettings(
    val darkTheme: Boolean = true,
    val indicatorStyle: IndicatorStyle = IndicatorStyle.REALISTIC,
    val smoothingLevel: SmoothingLevel = SmoothingLevel.STANDARD,
    val defaultTolerance: Float = 0.2f,
    val defaultUnit: AngleUnit = AngleUnit.DEGREES,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundMode: SoundMode = SoundMode.SINGLE,
    val volume: Float = 0.6f,
    val toneHz: Int = 880,
    val vibrationStrength: Int = 160,
    val vibrationDurationMs: Long = 80,
    val keepScreenOn: Boolean = true,
    val autoHoldSeconds: Int = 0,
    val cameraGrid: Boolean = true,
    val cameraVerticalLine: Boolean = true,
    val cameraHorizontalLine: Boolean = true,
    val overlayAlpha: Float = 0.85f,
    val lineWidth: Float = 2.5f
)
