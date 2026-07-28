package com.poziomnica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poziomnica.data.AppContainer
import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.IndicatorStyle
import com.poziomnica.domain.LevelReading
import com.poziomnica.domain.SmoothingLevel
import com.poziomnica.domain.SoundMode
import com.poziomnica.settings.SettingsRepository
import com.poziomnica.settings.UserSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalibrationUiState(
    val reading: LevelReading = LevelReading(),
    val planeSamples: List<LevelReading> = emptyList(),
    val edgeSamples: List<LevelReading> = emptyList(),
    val message: String = "Połóż telefon na stabilnej płaszczyźnie i zapisz pierwszy pomiar."
) {
    val nextStep: Int get() = planeSamples.size + 1
    val complete: Boolean get() = planeSamples.size >= 4
    val edgeNextStep: Int get() = edgeSamples.size + 1
    val edgeComplete: Boolean get() = edgeSamples.size >= 2
}

class CalibrationViewModel(private val container: AppContainer) : ViewModel() {
    val profiles = container.calibrationRepository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val internal = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = internal
    private var readingsJob: Job? = null

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                readingsJob?.cancel()
                readingsJob = viewModelScope.launch {
                    container.sensorManager.readings(settings.smoothingLevel).collect { reading ->
                        internal.value = internal.value.copy(reading = reading)
                    }
                }
            }
        }
    }

    fun quick(reading: LevelReading = internal.value.reading) = viewModelScope.launch { container.calibrationRepository.quickZero("Szybka kalibracja", reading) }
    fun twoSided(first: LevelReading, second: LevelReading) = viewModelScope.launch { container.calibrationRepository.twoSided("Kalibracja dwustronna", first, second) }
    fun capturePlanePoint() {
        val current = internal.value
        if (current.complete) return
        val samples = current.planeSamples + current.reading
        internal.value = current.copy(
            planeSamples = samples,
            message = when (samples.size) {
                1 -> "Obróć telefon o 90° i zapisz drugi pomiar."
                2 -> "Obróć telefon o kolejne 90° i zapisz trzeci pomiar."
                3 -> "Obróć telefon o kolejne 90° i zapisz czwarty pomiar."
                else -> "Komplet 4 pomiarów. Zatwierdź kalibrację."
            }
        )
    }
    fun savePlaneCalibration() = viewModelScope.launch {
        val samples = internal.value.planeSamples
        val correction = container.calibrationRepository.fourPointPlane("Tylna obudowa 4x90°", samples)
        internal.value = if (correction != null) {
            CalibrationUiState(message = "Zapisano korektę tylnej obudowy: pitch ${"%.2f".format(correction.first)}°, roll ${"%.2f".format(correction.second)}°.")
        } else {
            internal.value.copy(message = "Kalibracja tylnej obudowy wymaga dokładnie 4 pomiarów.")
        }
    }
    fun resetPlaneCalibrationFlow() {
        internal.value = CalibrationUiState(reading = internal.value.reading)
    }
    fun captureEdgePoint() {
        val current = internal.value
        if (current.edgeComplete) return
        val samples = current.edgeSamples + current.reading
        internal.value = current.copy(
            edgeSamples = samples,
            message = when (samples.size) {
                1 -> "Obróć telefon o 180° na tej samej krawędzi i zapisz drugi pomiar."
                else -> "Komplet 2 pomiarów krawędzi. Zatwierdź kalibrację."
            }
        )
    }
    fun saveEdgeCalibration() = viewModelScope.launch {
        val result = container.calibrationRepository.twoPointEdge("Krawędź 2x180°", internal.value.edgeSamples)
        internal.value = if (result != null) {
            CalibrationUiState(message = "Zapisano korektę krawędzi: ${result.first}, offset ${"%.2f".format(result.second)}°.")
        } else {
            internal.value.copy(message = "Kalibracja krawędzi wymaga dokładnie 2 pomiarów.")
        }
    }
    fun resetEdgeCalibrationFlow() {
        internal.value = internal.value.copy(edgeSamples = emptyList(), message = "Postaw telefon na kalibrowanej krawędzi i zapisz pierwszy pomiar.")
    }
    fun createProfile(name: String) = viewModelScope.launch { container.calibrationRepository.createProfile(name) }
    fun rename(id: String, name: String) = viewModelScope.launch { container.calibrationRepository.rename(id, name) }
    fun activate(id: String) = viewModelScope.launch { container.calibrationRepository.activate(id) }
    fun setDefault(id: String) = viewModelScope.launch { container.calibrationRepository.setDefault(id) }
    fun delete(id: String) = viewModelScope.launch { container.calibrationRepository.delete(id) }
    fun restore() = viewModelScope.launch { container.calibrationRepository.restoreFactory() }

    override fun onCleared() {
        readingsJob?.cancel()
    }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.settingsRepository
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())
    fun darkTheme(v: Boolean) = viewModelScope.launch { repo.setDarkTheme(v) }
    fun tolerance(v: Float) = viewModelScope.launch { repo.setTolerance(v) }
    fun unit(v: AngleUnit) = viewModelScope.launch { repo.setUnit(v) }
    fun smoothing(v: SmoothingLevel) = viewModelScope.launch { repo.setSmoothing(v) }
    fun style(v: IndicatorStyle) = viewModelScope.launch { repo.setStyle(v) }
    fun sound(v: Boolean) = viewModelScope.launch { repo.setSound(v) }
    fun soundMode(v: SoundMode) = viewModelScope.launch { repo.setSoundMode(v) }
    fun volume(v: Float) = viewModelScope.launch { repo.setVolume(v) }
    fun tone(v: Float) = viewModelScope.launch { repo.setTone(v.toInt()) }
    fun vibration(v: Boolean) = viewModelScope.launch { repo.setVibration(v) }
    fun vibrationStrength(v: Float) = viewModelScope.launch { repo.setVibrationStrength(v.toInt()) }
    fun vibrationDuration(v: Float) = viewModelScope.launch { repo.setVibrationDuration(v.toLong()) }
    fun cameraGrid(v: Boolean) = viewModelScope.launch { repo.setCameraGrid(v) }
    fun cameraVertical(v: Boolean) = viewModelScope.launch { repo.setCameraVertical(v) }
    fun cameraHorizontal(v: Boolean) = viewModelScope.launch { repo.setCameraHorizontal(v) }
    fun overlayAlpha(v: Float) = viewModelScope.launch { repo.setOverlayAlpha(v) }
    fun lineWidth(v: Float) = viewModelScope.launch { repo.setLineWidth(v) }
    fun keepOn(v: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(v) }
    fun autoHold(v: Int) = viewModelScope.launch { repo.setAutoHold(v) }
    fun testSound() = container.audioFeedback.test(settings.value)
    fun testVibration() = container.vibrationController.vibrate(settings.value)
    fun reset() = viewModelScope.launch { repo.reset() }
    fun resetAppearance() = viewModelScope.launch {
        val defaults = UserSettings()
        repo.setDarkTheme(defaults.darkTheme)
        repo.setStyle(defaults.indicatorStyle)
    }
    fun resetMeasurement() = viewModelScope.launch {
        val defaults = UserSettings()
        repo.setSmoothing(defaults.smoothingLevel)
        repo.setTolerance(defaults.defaultTolerance)
        repo.setUnit(defaults.defaultUnit)
        repo.setKeepScreenOn(defaults.keepScreenOn)
        repo.setAutoHold(defaults.autoHoldSeconds)
    }
    fun resetFeedback() = viewModelScope.launch {
        val defaults = UserSettings()
        repo.setSound(defaults.soundEnabled)
        repo.setSoundMode(defaults.soundMode)
        repo.setVolume(defaults.volume)
        repo.setTone(defaults.toneHz)
        repo.setVibration(defaults.vibrationEnabled)
        repo.setVibrationStrength(defaults.vibrationStrength)
        repo.setVibrationDuration(defaults.vibrationDurationMs)
    }
    fun resetCamera() = viewModelScope.launch {
        val defaults = UserSettings()
        repo.setCameraGrid(defaults.cameraGrid)
        repo.setCameraVertical(defaults.cameraVerticalLine)
        repo.setCameraHorizontal(defaults.cameraHorizontalLine)
        repo.setOverlayAlpha(defaults.overlayAlpha)
        repo.setLineWidth(defaults.lineWidth)
    }
}
