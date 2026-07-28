package com.poziomnica.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import com.poziomnica.domain.LevelReading
import com.poziomnica.domain.SmoothingLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.sqrt

class LevelSensorManager(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val filter = SmoothingFilter()

    val capabilityMessage: String
        get() {
            val names = preferredSensors().map { it.name }
            return if (names.isEmpty()) "Brak pełnego zestawu czujników. Pomiar może być niedostępny."
            else "Aktywne czujniki: ${names.joinToString()}"
        }

    fun readings(level: SmoothingLevel): Flow<LevelReading> = callbackFlow {
        filter.setLevel(level)
        val sensor = preferredSensors().firstOrNull()
        if (sensor == null) {
            trySend(LevelReading(sensorSummary = "Brak obsługiwanego czujnika", limitedAccuracy = true))
            awaitClose { }
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val raw = when (event.sensor.type) {
                    Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> fromGravity(event.values, event.accuracy)
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> fromRotationVector(event.values, event.accuracy)
                    else -> null
                } ?: return
                trySend(filter.filter(raw.copy(sensorSummary = capabilityMessage, limitedAccuracy = sensor.type == Sensor.TYPE_ACCELEROMETER)))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose {
            sensorManager.unregisterListener(listener)
            filter.reset()
        }
    }

    private fun preferredSensors(): List<Sensor> = listOfNotNull(
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    )

    private fun fromGravity(v: FloatArray, accuracy: Int): LevelReading {
        val x = v.getOrElse(0) { 0f }
        val y = v.getOrElse(1) { 0f }
        val z = v.getOrElse(2) { 0f }
        val pitch = Math.toDegrees(atan2((-x).toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
        val roll = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
        return normalizeForDisplay(pitch, roll, accuracy, edgeAngleFromGravity(x, y, z))
    }

    private fun fromRotationVector(v: FloatArray, accuracy: Int): LevelReading {
        val matrix = FloatArray(9)
        val adjusted = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, v)
        val rotation = windowManager.defaultDisplay.rotation
        val (axisX, axisY) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, axisX, axisY, adjusted)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(adjusted, orientation)
        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        return normalizeForDisplay(pitch, roll, accuracy, null).copy(azimuth = azimuth)
    }

    private fun normalizeForDisplay(pitch: Float, roll: Float, accuracy: Int, edge: Pair<Float, String>?): LevelReading {
        val linear = edge?.first ?: if (abs(roll) >= abs(pitch)) wrapToNearestRightAngle(roll) else wrapToNearestRightAngle(pitch)
        val normalizedRoll = wrap180(roll)
        val normalizedPitch = wrap180(pitch)
        return LevelReading(
            pitch = normalizedPitch,
            roll = normalizedRoll,
            linearAngle = linear,
            supportEdge = edge?.second ?: "krawędź wykrywana z rotacji",
            surfaceX = wrap90(normalizedRoll),
            surfaceY = wrap90(normalizedPitch),
            accuracyDegrees = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 0.1f
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 0.3f
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 0.8f
                else -> 1.2f
            }
        )
    }

    private fun edgeAngleFromGravity(x: Float, y: Float, z: Float): Pair<Float, String> {
        val g = sqrt((x * x + y * y + z * z).toDouble()).toFloat().coerceAtLeast(0.001f)
        val sideStanding = abs(z) < g * 0.68f
        if (!sideStanding) {
            val flatAngle = if (abs(x) >= abs(y)) Math.toDegrees(asin((x / g).coerceIn(-1f, 1f).toDouble())).toFloat()
            else Math.toDegrees(asin((-y / g).coerceIn(-1f, 1f).toDouble())).toFloat()
            return wrapToNearestRightAngle(flatAngle) to "tylna obudowa"
        }
        return if (abs(x) >= abs(y)) {
            val edge = if (x > 0f) "lewa długa krawędź" else "prawa długa krawędź"
            Math.toDegrees(asin((y / g).coerceIn(-1f, 1f).toDouble())).toFloat() to edge
        } else {
            val edge = if (y > 0f) "dolna krótka krawędź" else "górna krótka krawędź"
            Math.toDegrees(asin((-x / g).coerceIn(-1f, 1f).toDouble())).toFloat() to edge
        }
    }

    private fun wrap180(value: Float): Float {
        var v = value
        while (v > 180f) v -= 360f
        while (v < -180f) v += 360f
        return v
    }

    private fun wrap90(value: Float): Float {
        var v = wrap180(value)
        if (v > 90f) v = 180f - v
        if (v < -90f) v = -180f - v
        return v
    }

    private fun wrapToNearestRightAngle(value: Float): Float {
        val candidates = listOf(value, value - 90f, value + 90f, value - 180f, value + 180f)
        return candidates.minBy { abs(it) }.coerceIn(-90f, 90f)
    }
}
