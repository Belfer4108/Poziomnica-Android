package com.poziomnica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poziomnica.data.AppContainer
import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.LightReading
import com.poziomnica.domain.MeasurementDraft
import com.poziomnica.domain.MeasurementType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LightUiState(
    val reading: LightReading = LightReading(),
    val heldReading: LightReading? = null,
    val message: String = "",
    val savedMessage: String? = null,
    val isHeld: Boolean = false
)

class LightMeterViewModel(private val container: AppContainer) : ViewModel() {
    private val internal = MutableStateFlow(LightUiState())
    val uiState: StateFlow<LightUiState> = internal
    private var job: Job? = null
    private var minLux = Float.MAX_VALUE
    private var maxLux = 0f
    private var sumLux = 0f
    private var count = 0

    fun start() {
        if (job?.isActive == true) return
        job?.cancel()
        job = viewModelScope.launch {
            container.toolSensorManager.lightReadings().collect { reading ->
                val stableReading = if (reading.available) {
                    minLux = minOf(minLux, reading.lux)
                    maxLux = maxOf(maxLux, reading.lux)
                    count += 1
                    sumLux += reading.lux
                    reading.copy(
                        minLux = if (minLux == Float.MAX_VALUE) reading.lux else minLux,
                        maxLux = maxLux,
                        averageLux = if (count > 0) sumLux / count else reading.lux
                    )
                } else {
                    reading
                }
                internal.value = internal.value.copy(
                    reading = stableReading,
                    message = if (!stableReading.available) "Ten telefon nie udostępnia czujnika światła." else lightDescription(stableReading.lux)
                )
            }
        }
    }

    fun toggleHold() {
        val state = internal.value
        internal.value = if (state.heldReading == null) {
            state.copy(heldReading = state.reading, isHeld = true, savedMessage = null)
        } else {
            state.copy(heldReading = null, isHeld = false, savedMessage = null)
        }
    }

    fun resetStats() {
        minLux = Float.MAX_VALUE
        maxLux = 0f
        sumLux = 0f
        count = 0
        internal.value = internal.value.copy(heldReading = null, isHeld = false, savedMessage = null)
        start()
    }

    fun save() = viewModelScope.launch {
        val r = internal.value.heldReading ?: internal.value.reading
        container.measurementRepository.save(
            MeasurementDraft(
                name = "Luksomierz ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                type = MeasurementType.LIGHT,
                mainValue = r.lux,
                xValue = r.minLux,
                yValue = r.maxLux,
                unit = AngleUnit.LUX,
                tolerance = 0f,
                targetReached = true,
                note = "Średnia ${"%.1f".format(r.averageLux)} lx"
            )
        )
        internal.value = internal.value.copy(savedMessage = "Zapisano pomiar światła")
    }

    fun stop() { job?.cancel() }
    override fun onCleared() = stop()

    private fun lightDescription(lux: Float): String = when {
        lux < 5f -> "Bardzo ciemno"
        lux < 100f -> "Słabe oświetlenie"
        lux < 300f -> "Umiarkowane oświetlenie"
        lux < 750f -> "Dobre oświetlenie robocze"
        else -> "Bardzo jasne światło"
    }
}
