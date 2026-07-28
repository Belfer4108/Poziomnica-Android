package com.poziomnica.sensors

import com.poziomnica.domain.LevelReading
import com.poziomnica.domain.SmoothingLevel

class SmoothingFilter(level: SmoothingLevel = SmoothingLevel.STANDARD) {
    private var alpha = level.alpha
    private var last: LevelReading? = null

    fun setLevel(level: SmoothingLevel) {
        alpha = level.alpha
    }

    fun reset() {
        last = null
    }

    fun filter(reading: LevelReading): LevelReading {
        val previous = last
        if (previous == null) {
            last = reading
            return reading
        }
        fun smooth(old: Float, new: Float) = old + alpha * (new - old)
        val filtered = reading.copy(
            pitch = smooth(previous.pitch, reading.pitch),
            roll = smooth(previous.roll, reading.roll),
            azimuth = smooth(previous.azimuth, reading.azimuth),
            linearAngle = smooth(previous.linearAngle, reading.linearAngle),
            supportEdge = reading.supportEdge,
            surfaceX = smooth(previous.surfaceX, reading.surfaceX),
            surfaceY = smooth(previous.surfaceY, reading.surfaceY),
            accuracyDegrees = smooth(previous.accuracyDegrees, reading.accuracyDegrees)
        )
        last = filtered
        return filtered
    }
}
