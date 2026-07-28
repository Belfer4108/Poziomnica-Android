package com.poziomnica.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.IndicatorStyle
import com.poziomnica.domain.SmoothingLevel
import com.poziomnica.domain.SoundMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val darkTheme = booleanPreferencesKey("dark_theme")
        val style = stringPreferencesKey("style")
        val smoothing = stringPreferencesKey("smoothing")
        val tolerance = floatPreferencesKey("tolerance")
        val unit = stringPreferencesKey("unit")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val soundMode = stringPreferencesKey("sound_mode")
        val volume = floatPreferencesKey("volume")
        val tone = intPreferencesKey("tone")
        val vibStrength = intPreferencesKey("vib_strength")
        val vibMs = intPreferencesKey("vib_ms")
        val keepOn = booleanPreferencesKey("keep_on")
        val autoHold = intPreferencesKey("auto_hold")
        val grid = booleanPreferencesKey("grid")
        val vertical = booleanPreferencesKey("vertical")
        val horizontal = booleanPreferencesKey("horizontal")
        val overlay = floatPreferencesKey("overlay")
        val lineWidth = floatPreferencesKey("line_width")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { p ->
        UserSettings(
            darkTheme = p[Keys.darkTheme] ?: true,
            indicatorStyle = enumValueOrDefault(p[Keys.style], IndicatorStyle.REALISTIC),
            smoothingLevel = enumValueOrDefault(p[Keys.smoothing], SmoothingLevel.STANDARD),
            defaultTolerance = p[Keys.tolerance] ?: 0.2f,
            defaultUnit = enumValueOrDefault(p[Keys.unit], AngleUnit.DEGREES).takeUnless { it == AngleUnit.RATIO || it == AngleUnit.LUX } ?: AngleUnit.DEGREES,
            soundEnabled = p[Keys.sound] ?: true,
            vibrationEnabled = p[Keys.vibration] ?: true,
            soundMode = enumValueOrDefault(p[Keys.soundMode], SoundMode.SINGLE),
            volume = p[Keys.volume] ?: 0.6f,
            toneHz = p[Keys.tone] ?: 880,
            vibrationStrength = p[Keys.vibStrength] ?: 160,
            vibrationDurationMs = (p[Keys.vibMs] ?: 80).toLong(),
            keepScreenOn = p[Keys.keepOn] ?: true,
            autoHoldSeconds = p[Keys.autoHold] ?: 0,
            cameraGrid = p[Keys.grid] ?: true,
            cameraVerticalLine = p[Keys.vertical] ?: true,
            cameraHorizontalLine = p[Keys.horizontal] ?: true,
            overlayAlpha = p[Keys.overlay] ?: 0.85f,
            lineWidth = p[Keys.lineWidth] ?: 2.5f
        )
    }

    suspend fun setTolerance(value: Float) = edit { it[Keys.tolerance] = value.coerceIn(0.05f, 5f) }
    suspend fun setDarkTheme(value: Boolean) = edit { it[Keys.darkTheme] = value }
    suspend fun setUnit(value: AngleUnit) = edit { it[Keys.unit] = (value.takeUnless { it == AngleUnit.RATIO || it == AngleUnit.LUX } ?: AngleUnit.DEGREES).name }
    suspend fun setSmoothing(value: SmoothingLevel) = edit { it[Keys.smoothing] = value.name }
    suspend fun setSound(value: Boolean) = edit { it[Keys.sound] = value }
    suspend fun setSoundMode(value: SoundMode) = edit { it[Keys.soundMode] = value.name }
    suspend fun setVibration(value: Boolean) = edit { it[Keys.vibration] = value }
    suspend fun setStyle(value: IndicatorStyle) = edit { it[Keys.style] = value.name }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepOn] = value }
    suspend fun setAutoHold(seconds: Int) = edit { it[Keys.autoHold] = seconds }
    suspend fun setCameraGrid(value: Boolean) = edit { it[Keys.grid] = value }
    suspend fun setCameraVertical(value: Boolean) = edit { it[Keys.vertical] = value }
    suspend fun setCameraHorizontal(value: Boolean) = edit { it[Keys.horizontal] = value }
    suspend fun setOverlayAlpha(value: Float) = edit { it[Keys.overlay] = value.coerceIn(0.15f, 1f) }
    suspend fun setLineWidth(value: Float) = edit { it[Keys.lineWidth] = value.coerceIn(1f, 8f) }
    suspend fun setVolume(value: Float) = edit { it[Keys.volume] = value.coerceIn(0f, 1f) }
    suspend fun setTone(value: Int) = edit { it[Keys.tone] = value.coerceIn(220, 2200) }
    suspend fun setVibrationStrength(value: Int) = edit { it[Keys.vibStrength] = value.coerceIn(1, 255) }
    suspend fun setVibrationDuration(value: Long) = edit { it[Keys.vibMs] = value.toInt().coerceIn(20, 1000) }
    suspend fun reset() = context.settingsDataStore.edit { it.clear() }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
