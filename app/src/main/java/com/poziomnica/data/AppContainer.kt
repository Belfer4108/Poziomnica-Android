package com.poziomnica.data

import android.content.Context
import com.poziomnica.audio.AudioFeedbackController
import com.poziomnica.calibration.CalibrationRepository
import com.poziomnica.database.AppDatabase
import com.poziomnica.export.ExportManager
import com.poziomnica.repository.MeasurementRepository
import com.poziomnica.sensors.LevelSensorManager
import com.poziomnica.sensors.ToolSensorManager
import com.poziomnica.settings.SettingsRepository
import com.poziomnica.vibration.VibrationController

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.get(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val calibrationRepository = CalibrationRepository(appContext)
    val measurementRepository = MeasurementRepository(database.measurementDao())
    val sensorManager = LevelSensorManager(appContext)
    val toolSensorManager = ToolSensorManager(appContext)
    val exportManager = ExportManager(appContext)
    val audioFeedback = AudioFeedbackController()
    val vibrationController = VibrationController(appContext)
}
