package com.poziomnica.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan

object MeasurementMath {
    fun degreesToPercent(degrees: Float): Float = tan(degrees.toRadians()) * 100f

    fun degreesToMmPerMeter(degrees: Float): Float = tan(degrees.toRadians()) * 1000f

    fun degreesToCmPerMeter(degrees: Float): Float = degreesToMmPerMeter(degrees) / 10f

    fun heightDifferenceMm(degrees: Float, lengthMeters: Float): Float =
        degreesToMmPerMeter(degrees) * lengthMeters.coerceAtLeast(0f)

    fun heightDifferenceCm(degrees: Float, lengthMeters: Float): Float =
        heightDifferenceMm(degrees, lengthMeters) / 10f

    fun mmPerMeterToRatio(mmPerMeter: Float): Float {
        if (abs(mmPerMeter) < 0.0001f) return Float.POSITIVE_INFINITY
        return 1000f / abs(mmPerMeter)
    }

    fun valueToDegrees(value: Float, unit: AngleUnit): Float = when (unit) {
        AngleUnit.DEGREES -> value
        AngleUnit.PERCENT -> atan(value / 100f).toDegrees()
        AngleUnit.MM_PER_M -> atan(value / 1000f).toDegrees()
        AngleUnit.CM_PER_M -> atan((value * 10f) / 1000f).toDegrees()
        AngleUnit.RATIO -> if (abs(value) < 0.0001f) 0f else atan(1f / value).toDegrees()
        AngleUnit.LUX -> value
        AngleUnit.METERS -> value
        AngleUnit.CENTIMETERS -> value / 100f
        AngleUnit.SQUARE_METERS -> value
        AngleUnit.CUBIC_METERS -> value
    }

    fun formatByUnit(degrees: Float, unit: AngleUnit): String = when (unit) {
        AngleUnit.DEGREES -> "%+.2f°".format(degrees)
        AngleUnit.PERCENT -> "%+.2f%%".format(degreesToPercent(degrees))
        AngleUnit.MM_PER_M -> "%+.1f mm/m".format(degreesToMmPerMeter(degrees))
        AngleUnit.CM_PER_M -> "%+.2f cm/m".format(degreesToCmPerMeter(degrees))
        AngleUnit.RATIO -> {
            val ratio = mmPerMeterToRatio(degreesToMmPerMeter(degrees))
            if (ratio.isInfinite()) "poziom" else "1:%.0f".format(ratio)
        }
        AngleUnit.LUX -> "%.1f lx".format(degrees)
        AngleUnit.METERS -> "%.3f m".format(degrees)
        AngleUnit.CENTIMETERS -> "%.1f cm".format(degrees * 100f)
        AngleUnit.SQUARE_METERS -> "%.3f m²".format(degrees)
        AngleUnit.CUBIC_METERS -> "%.3f m³".format(degrees)
    }

    fun isWithinTolerance(value: Float, target: Float, tolerance: Float): Boolean =
        abs(value - target) <= abs(tolerance)

    fun angleBetweenSurfaces(first: Float, second: Float): Float =
        abs(second - first).coerceIn(0f, 180f)

    fun twoSidedCalibrationError(first: LevelReading, second: LevelReading): Pair<Float, Float> =
        Pair((first.pitch + second.pitch) / 2f, (first.roll + second.roll) / 2f)

    private fun Float.toRadians(): Float = (this * PI / 180.0).toFloat()
    private fun Float.toDegrees(): Float = (this * 180.0 / PI).toFloat()
}
