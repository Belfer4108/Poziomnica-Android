package com.poziomnica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.poziomnica.data.AppContainer
import com.poziomnica.domain.MeasurementType

class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(container.settingsRepository) as T
            LinearLevelViewModel::class.java -> LinearLevelViewModel(container, MeasurementType.LINEAR_LEVEL) as T
            SurfaceLevelViewModel::class.java -> SurfaceLevelViewModel(container) as T
            PlumbViewModel::class.java -> PlumbViewModel(container) as T
            SlopeViewModel::class.java -> SlopeViewModel(container) as T
            ProtractorViewModel::class.java -> ProtractorViewModel(container) as T
            CameraLevelViewModel::class.java -> CameraLevelViewModel(container) as T
            HistoryViewModel::class.java -> HistoryViewModel(container) as T
            CalibrationViewModel::class.java -> CalibrationViewModel(container) as T
            SettingsViewModel::class.java -> SettingsViewModel(container) as T
            LightMeterViewModel::class.java -> LightMeterViewModel(container) as T
            TapeMeasureViewModel::class.java -> TapeMeasureViewModel(container) as T
            else -> throw IllegalArgumentException("Nieznany ViewModel: ${modelClass.name}")
        }
    }
}
