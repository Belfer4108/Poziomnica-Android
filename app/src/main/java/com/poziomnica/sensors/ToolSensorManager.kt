package com.poziomnica.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.poziomnica.domain.LightReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ToolSensorManager(context: Context) {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun lightReadings(): Flow<LightReading> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) {
            trySend(LightReading(available = false))
            awaitClose { }
            return@callbackFlow
        }
        var min = Float.MAX_VALUE
        var max = 0f
        var sum = 0f
        var count = 0
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values.firstOrNull() ?: 0f
                min = minOf(min, lux)
                max = maxOf(max, lux)
                count += 1
                sum += lux
                trySend(LightReading(lux = lux, minLux = min, maxLux = max, averageLux = sum / count))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
