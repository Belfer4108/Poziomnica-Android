package com.poziomnica.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.poziomnica.ui.screens.CameraLevelScreen
import com.poziomnica.ui.screens.CalibrationScreen
import com.poziomnica.ui.screens.CalculatorsScreen
import com.poziomnica.ui.screens.FaqScreen
import com.poziomnica.ui.screens.HistoryScreen
import com.poziomnica.ui.screens.HomeScreen
import com.poziomnica.ui.screens.LightMeterScreen
import com.poziomnica.ui.screens.LinearLevelScreen
import com.poziomnica.ui.screens.PlumbScreen
import com.poziomnica.ui.screens.ProtractorScreen
import com.poziomnica.ui.screens.SettingsScreen
import com.poziomnica.ui.screens.SlopeScreen
import com.poziomnica.ui.screens.SurfaceLevelScreen
import com.poziomnica.viewmodel.*

object Routes {
    const val HOME = "home"
    const val LINEAR = "linear"
    const val SURFACE = "surface"
    const val PLUMB = "plumb"
    const val CAMERA = "camera"
    const val SLOPE = "slope"
    const val PROTRACTOR = "protractor"
    const val HISTORY = "history"
    const val CALIBRATION = "calibration"
    const val SETTINGS = "settings"
    const val LIGHT = "light"
    const val FAQ = "faq"
    const val CALCULATORS = "calculators"
}

@Composable
fun AppNavigation(nav: NavHostController, factory: ViewModelFactory) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(nav, viewModel(factory = factory)) }
        composable(Routes.LINEAR) { LinearLevelScreen(nav, viewModel<LinearLevelViewModel>(factory = factory)) }
        composable(Routes.SURFACE) { SurfaceLevelScreen(nav, viewModel(factory = factory)) }
        composable(Routes.PLUMB) { PlumbScreen(nav, viewModel(factory = factory)) }
        composable(Routes.CAMERA) { CameraLevelScreen(nav, viewModel(factory = factory)) }
        composable(Routes.SLOPE) { SlopeScreen(nav, viewModel(factory = factory)) }
        composable(Routes.PROTRACTOR) { ProtractorScreen(nav, viewModel(factory = factory)) }
        composable(Routes.HISTORY) { HistoryScreen(nav, viewModel(factory = factory)) }
        composable(Routes.CALIBRATION) { CalibrationScreen(nav, viewModel(factory = factory)) }
        composable(Routes.SETTINGS) { SettingsScreen(nav, viewModel(factory = factory)) }
        composable(Routes.LIGHT) { LightMeterScreen(nav, viewModel(factory = factory)) }
        composable(Routes.FAQ) { FaqScreen(nav) }
        composable(Routes.CALCULATORS) { CalculatorsScreen(nav) }
    }
}
