package com.poziomnica

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.poziomnica.data.AppContainer
import com.poziomnica.navigation.AppNavigation
import com.poziomnica.ui.theme.PoziomnicaTheme
import com.poziomnica.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val container = remember { AppContainer(applicationContext) }
            val factory = remember { ViewModelFactory(container) }
            val nav = rememberNavController()
            val settings = container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = com.poziomnica.settings.UserSettings())
            DisposableEffect(settings.value.keepScreenOn) {
                if (settings.value.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            PoziomnicaTheme(darkTheme = settings.value.darkTheme, dynamicColor = false) {
                AppNavigation(nav = nav, factory = factory)
            }
        }
    }
}
