package com.poziomnica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poziomnica.camera.ArPlaneKind
import com.poziomnica.data.AppContainer
import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.MeasurementDraft
import com.poziomnica.domain.MeasurementType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class TapeMeasureUnit(val label: String) {
    MILLIMETERS("mm"),
    CENTIMETERS("cm"),
    METERS("m")
}

data class ArPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val screenX: Float? = null,
    val screenY: Float? = null,
    val distanceFromCameraMeters: Float? = null,
    val planeKind: ArPlaneKind = ArPlaneKind.UNKNOWN
)

data class TapeMeasureUiState(
    val unit: TapeMeasureUnit = TapeMeasureUnit.CENTIMETERS,
    val startPoint: ArPoint? = null,
    val endPoint: ArPoint? = null,
    val livePoint: ArPoint? = null,
    val measurementMeters: Float? = null,
    val liveMeters: Float? = null,
    val trackingQuality: String = "Uruchamianie ARCore",
    val targetAvailable: Boolean = false,
    val message: String = "Skieruj środek ekranu na wyraźną powierzchnię.",
    val savedMessage: String? = null
)

class TapeMeasureViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(TapeMeasureUiState())
    val uiState: StateFlow<TapeMeasureUiState> = _uiState.asStateFlow()

    fun setUnit(unit: TapeMeasureUnit) {
        _uiState.update { it.copy(unit = unit) }
    }

    fun updateTracking(quality: String, targetAvailable: Boolean, currentPoint: ArPoint?) {
        _uiState.update { state ->
            val liveDistance = state.startPoint?.let { start ->
                currentPoint?.let { current -> distance(start, current) }
            }
            state.copy(
                trackingQuality = quality,
                targetAvailable = targetAvailable,
                livePoint = currentPoint,
                liveMeters = liveDistance,
                message = when {
                    state.startPoint == null && targetAvailable -> "Punkt gotowy. Naciśnij Start i rozwiń taśmę ruchem telefonu."
                    state.startPoint != null && state.endPoint == null && targetAvailable -> "Taśma rozwinięta. Naciśnij Koniec, aby zatwierdzić pomiar."
                    state.endPoint != null -> "Pomiar zakończony. Możesz zapisać wynik albo zresetować miarkę."
                    else -> quality
                }
            )
        }
    }

    fun markPoint(point: ArPoint?) {
        if (point == null) {
            _uiState.update { it.copy(message = "Brak trafienia w powierzchnię. Ustaw celownik na wykrytej płaszczyźnie.") }
            return
        }
        _uiState.update { state ->
            when {
                state.startPoint == null || state.endPoint != null -> state.copy(
                    startPoint = point,
                    endPoint = null,
                    livePoint = point,
                    measurementMeters = null,
                    liveMeters = null,
                    savedMessage = null,
                    message = "Start taśmy zapisany. Przesuń telefon do drugiego punktu."
                )
                else -> {
                    val meters = distance(state.startPoint, point)
                    state.copy(
                        endPoint = point,
                        measurementMeters = meters,
                        liveMeters = meters,
                        savedMessage = null,
                        message = "Koniec taśmy zapisany. Wynik jest gotowy."
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                startPoint = null,
                endPoint = null,
                livePoint = null,
                measurementMeters = null,
                liveMeters = null,
                savedMessage = null,
                message = "Skieruj środek ekranu na wyraźną powierzchnię."
            )
        }
    }

    fun save(photoUri: String? = null) = viewModelScope.launch {
        val state = _uiState.value
        val meters = state.measurementMeters ?: state.liveMeters
        if (meters == null) {
            _uiState.update { it.copy(message = "Brak wyniku do zapisania.") }
            return@launch
        }
        container.measurementRepository.save(
            MeasurementDraft(
                name = "Miarka ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                type = MeasurementType.TAPE_MEASURE,
                mainValue = meters,
                xValue = state.startPoint?.x,
                yValue = state.endPoint?.x,
                unit = AngleUnit.METERS,
                tolerance = 0f,
                targetReached = true,
                note = "Miarka AR, wynik ${formatDistance(meters)}, jakość: ${state.trackingQuality}",
                photoUri = photoUri
            )
        )
        _uiState.update { it.copy(savedMessage = "Zapisano pomiar miarki w historii.") }
    }

    fun formatDistance(meters: Float?, unit: TapeMeasureUnit = _uiState.value.unit): String {
        meters ?: return "--"
        return when (unit) {
            TapeMeasureUnit.MILLIMETERS -> "%.0f mm".format(meters * 1000f)
            TapeMeasureUnit.CENTIMETERS -> "%.1f cm".format(meters * 100f)
            TapeMeasureUnit.METERS -> "%.3f m".format(meters)
        }
    }

    private fun distance(a: ArPoint, b: ArPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
