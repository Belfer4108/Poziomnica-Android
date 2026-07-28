package com.poziomnica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poziomnica.data.AppContainer
import com.poziomnica.database.MeasurementEntity
import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.CalibrationProfile
import com.poziomnica.domain.LevelReading
import com.poziomnica.domain.MeasurementDraft
import com.poziomnica.domain.MeasurementMath
import com.poziomnica.domain.MeasurementType
import com.poziomnica.domain.SlopeDirection
import com.poziomnica.domain.SoundMode
import com.poziomnica.settings.UserSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class MeasurementUiState(
    val reading: LevelReading = LevelReading(),
    val heldReading: LevelReading? = null,
    val settings: UserSettings = UserSettings(),
    val status: String = "Oczekiwanie na odczyt",
    val savedMessage: String? = null,
    val target: Float = 0f,
    val targetReached: Boolean = false,
    val stable: Boolean = false
)

open class LiveMeasurementViewModel(
    protected val container: AppContainer,
    private val type: MeasurementType
) : ViewModel() {
    private val internal = MutableStateFlow(MeasurementUiState())
    val uiState: StateFlow<MeasurementUiState> = internal
    private var sensorJob: Job? = null
    private var lastStableSince = 0L

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                internal.value = internal.value.copy(settings = settings)
                restartSensors(settings)
            }
        }
    }

    fun start() = restartSensors(internal.value.settings)
    fun stop() {
        sensorJob?.cancel()
        container.audioFeedback.stop()
    }

    fun toggleHold() {
        internal.value = internal.value.let { state ->
            state.copy(heldReading = if (state.heldReading == null) state.reading else null)
        }
    }

    fun zero() {
        viewModelScope.launch {
            container.calibrationRepository.quickZero("Szybkie zero", internal.value.reading)
            internal.value = internal.value.copy(savedMessage = "Zapisano punkt zerowy")
        }
    }

    fun save(note: String = "") {
        viewModelScope.launch {
            val state = internal.value
            val r = state.heldReading ?: state.reading
            container.measurementRepository.save(
                MeasurementDraft(
                    name = "${type.label} ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                    type = type,
                    mainValue = primaryValue(r),
                    xValue = r.surfaceX,
                    yValue = r.surfaceY,
                    unit = state.settings.defaultUnit,
                    tolerance = state.settings.defaultTolerance,
                    targetReached = state.targetReached,
                    note = note,
                    targetValue = state.target
                )
            )
            internal.value = state.copy(savedMessage = "Pomiar zapisany")
        }
    }

    fun setTolerance(value: Float) = viewModelScope.launch { container.settingsRepository.setTolerance(value) }
    fun setUnit(unit: AngleUnit) = viewModelScope.launch { container.settingsRepository.setUnit(unit) }
    fun setSound(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.setSound(enabled) }
    fun setSoundMode(mode: SoundMode) = viewModelScope.launch { container.settingsRepository.setSoundMode(mode) }
    fun setVibration(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.setVibration(enabled) }

    protected open fun primaryValue(reading: LevelReading): Float = reading.linearAngle
    protected open fun targetValue(): Float = 0f
    protected open fun statusFor(reading: LevelReading, settings: UserSettings, reached: Boolean): String =
        if (reached) "${type.label}: wartość osiągnięta" else "${type.label}: koryguj ustawienie"

    private fun restartSensors(settings: UserSettings) {
        sensorJob?.cancel()
        sensorJob = viewModelScope.launch {
            combine(
                container.sensorManager.readings(settings.smoothingLevel),
                container.calibrationRepository.profiles
            ) { reading, profiles ->
                val active = profiles.firstOrNull { it.isActive }
                if (active == null || active.id == "factory") reading else reading.withCalibration(active)
            }.collect { reading ->
                val target = targetValue()
                val value = primaryValue(reading)
                val reached = MeasurementMath.isWithinTolerance(value, target, settings.defaultTolerance)
                val stable = updateStability(reading, settings)
                container.audioFeedback.update(reached, abs(value - target), settings, viewModelScope)
                container.vibrationController.update(reached, settings)
                internal.value = internal.value.copy(
                    reading = reading,
                    target = target,
                    targetReached = reached,
                    stable = stable,
                    status = statusFor(reading, settings, reached)
                )
            }
        }
    }

    private fun LevelReading.withCalibration(profile: CalibrationProfile): LevelReading {
        val linearOffset = when {
            supportEdge.contains("krótka", ignoreCase = true) -> profile.shortEdgeOffset
            supportEdge.contains("długa", ignoreCase = true) -> profile.longEdgeOffset
            else -> if (abs(roll) >= abs(pitch)) profile.offsetRoll else profile.offsetPitch
        }
        return copy(
            pitch = pitch - profile.offsetPitch,
            roll = roll - profile.offsetRoll,
            linearAngle = linearAngle - linearOffset,
            surfaceX = surfaceX - profile.offsetRoll,
            surfaceY = surfaceY - profile.offsetPitch
        )
    }

    private fun updateStability(reading: LevelReading, settings: UserSettings): Boolean {
        val now = System.currentTimeMillis()
        val stable = reading.accuracyDegrees <= 0.5f
        if (!stable) lastStableSince = 0L
        if (stable && lastStableSince == 0L) lastStableSince = now
        val held = settings.autoHoldSeconds > 0 && stable && now - lastStableSince >= settings.autoHoldSeconds * 1000L
        if (held && internal.value.heldReading == null) internal.value = internal.value.copy(heldReading = reading)
        return stable
    }

    override fun onCleared() {
        stop()
    }
}

class LinearLevelViewModel(container: AppContainer, type: MeasurementType) : LiveMeasurementViewModel(container, type) {
    private val targetDegrees = MutableStateFlow(0f)
    override fun targetValue(): Float = targetDegrees.value
    override fun statusFor(reading: LevelReading, settings: UserSettings, reached: Boolean): String {
        val diff = reading.linearAngle - targetDegrees.value
        val amount = MeasurementMath.formatByUnit(abs(diff), settings.defaultUnit)
        return when {
            reached -> "Kąt ustawiony"
            diff < 0f -> "Podnieś o $amount"
            else -> "Opuść o $amount"
        }
    }
    fun setTarget(value: Float, unit: AngleUnit) {
        targetDegrees.value = MeasurementMath.valueToDegrees(value, unit).coerceIn(-45f, 45f)
    }
}

class SurfaceLevelViewModel(container: AppContainer) : LiveMeasurementViewModel(container, MeasurementType.SURFACE_LEVEL) {
    override fun primaryValue(reading: LevelReading): Float = reading.totalSurfaceDeviation
    override fun statusFor(reading: LevelReading, settings: UserSettings, reached: Boolean): String =
        if (reached) "Poziom osiągnięty" else "Przesuń bąbelek do środka"
}

class PlumbViewModel(container: AppContainer) : LiveMeasurementViewModel(container, MeasurementType.PLUMB) {
    override fun primaryValue(reading: LevelReading): Float = 90f - abs(reading.roll)
    override fun statusFor(reading: LevelReading, settings: UserSettings, reached: Boolean): String =
        if (reached) "Pion osiągnięty" else "Koryguj pion"
    fun savePlumb() {
        viewModelScope.launch {
            val state = uiState.value
            val r = state.heldReading ?: state.reading
            val deviation = primaryValue(r)
            container.measurementRepository.save(
                MeasurementDraft(
                    name = "Pion ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                    type = MeasurementType.PLUMB,
                    mainValue = deviation,
                    xValue = r.roll,
                    yValue = r.pitch,
                    unit = AngleUnit.DEGREES,
                    tolerance = state.settings.defaultTolerance,
                    targetReached = state.targetReached,
                    note = "Krawędź: ${r.supportEdge}, odchylenie od pionu ${"%.2f".format(deviation)}°",
                    targetValue = 0f
                )
            )
        }
    }
}

class SlopeViewModel(container: AppContainer) : LiveMeasurementViewModel(container, MeasurementType.SLOPE) {
    private val targetDegrees = MutableStateFlow(MeasurementMath.valueToDegrees(1f, AngleUnit.PERCENT))
    private val direction = MutableStateFlow(SlopeDirection.RIGHT)
    val selectedDirection: StateFlow<SlopeDirection> = direction
    override fun targetValue(): Float = targetDegrees.value
    override fun statusFor(reading: LevelReading, settings: UserSettings, reached: Boolean): String {
        val diff = primaryValue(reading) - targetDegrees.value
        return when {
            reached -> "Spadek prawidłowy"
            diff < 0f -> "Za mały spadek, przechyl ${direction.value.label}"
            else -> "Za duży spadek, zmniejsz nachylenie"
        }
    }
    fun setTarget(value: Float, unit: AngleUnit) { targetDegrees.value = MeasurementMath.valueToDegrees(value, unit) }
    fun setDirection(value: SlopeDirection) { direction.value = value }
    fun saveSlope(unit: AngleUnit) {
        viewModelScope.launch {
            val state = uiState.value
            val r = state.heldReading ?: state.reading
            container.measurementRepository.save(
                MeasurementDraft(
                    name = "Spadek ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                    type = MeasurementType.SLOPE,
                    mainValue = primaryValue(r),
                    xValue = r.surfaceX,
                    yValue = r.surfaceY,
                    unit = unit,
                    tolerance = state.settings.defaultTolerance,
                    targetReached = state.targetReached,
                    note = "Cel ${MeasurementMath.formatByUnit(state.target, unit)}, kierunek ${direction.value.label}",
                    targetValue = state.target
                )
            )
        }
    }
}

class ProtractorViewModel(container: AppContainer) : LiveMeasurementViewModel(container, MeasurementType.PROTRACTOR) {
    private val firstSurface = MutableStateFlow<Float?>(null)
    private val measuredAngle = MutableStateFlow<Float?>(null)
    private val target = MutableStateFlow(0f)
    val baseAngle: StateFlow<Float?> = firstSurface
    val capturedAngle: StateFlow<Float?> = measuredAngle
    override fun primaryValue(reading: LevelReading): Float =
        measuredAngle.value ?: firstSurface.value?.let { MeasurementMath.angleBetweenSurfaces(it, reading.linearAngle) } ?: reading.linearAngle
    override fun targetValue(): Float = target.value
    override fun statusFor(reading: LevelReading, settings: UserSettings, reached: Boolean): String = when {
        firstSurface.value == null -> "Ustaw telefon na pierwszej powierzchni i naciśnij Baza"
        measuredAngle.value == null -> "Przechyl telefon do drugiej powierzchni i naciśnij Nachylenie"
        reached -> "Kąt docelowy osiągnięty"
        else -> "Zmierzony kąt zapisany"
    }
    fun rememberFirstSurface() {
        firstSurface.value = uiState.value.reading.linearAngle
        measuredAngle.value = null
    }
    fun captureInclination() {
        val current = uiState.value.reading.linearAngle
        measuredAngle.value = firstSurface.value?.let { MeasurementMath.angleBetweenSurfaces(it, current) } ?: current
    }
    fun clearFirstSurface() {
        firstSurface.value = null
        measuredAngle.value = null
    }
    fun setTarget(degrees: Float) { target.value = degrees.coerceIn(0f, 180f) }
    fun setTargetToCurrent() { target.value = primaryValue(uiState.value.reading).coerceIn(0f, 180f) }
}

class CameraLevelViewModel(container: AppContainer) : LiveMeasurementViewModel(container, MeasurementType.CAMERA) {
    fun setGrid(value: Boolean) = viewModelScope.launch { container.settingsRepository.setCameraGrid(value) }
    fun setVertical(value: Boolean) = viewModelScope.launch { container.settingsRepository.setCameraVertical(value) }
    fun setHorizontal(value: Boolean) = viewModelScope.launch { container.settingsRepository.setCameraHorizontal(value) }
    fun savePhotoMeasurement(photoUri: String, note: String = "Zdjęcie z aparatu") {
        viewModelScope.launch {
            val state = uiState.value
            val reading = state.heldReading ?: state.reading
            container.measurementRepository.save(
                MeasurementDraft(
                    name = "Aparat ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                    type = MeasurementType.CAMERA,
                    mainValue = reading.roll,
                    xValue = reading.roll,
                    yValue = reading.pitch,
                    unit = state.settings.defaultUnit,
                    tolerance = state.settings.defaultTolerance,
                    targetReached = state.targetReached,
                    note = note,
                    photoUri = photoUri
                )
            )
        }
    }
}

class HomeViewModel(settingsRepository: com.poziomnica.settings.SettingsRepository) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())
}

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val measurements = container.measurementRepository.measurements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val query = MutableStateFlow("")
    val visible = combine(measurements, query) { list, q ->
        if (q.isBlank()) list else list.filter { it.name.contains(q, true) || it.note.contains(q, true) || it.type.contains(q, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun setQuery(value: String) { query.value = value }
    fun update(entity: MeasurementEntity, name: String, note: String) = viewModelScope.launch {
        container.measurementRepository.update(entity.copy(name = name.ifBlank { entity.name }, note = note))
    }
    fun delete(entity: MeasurementEntity) = viewModelScope.launch { container.measurementRepository.delete(entity) }
    fun duplicate(entity: MeasurementEntity) = viewModelScope.launch { container.measurementRepository.duplicate(entity) }
}
