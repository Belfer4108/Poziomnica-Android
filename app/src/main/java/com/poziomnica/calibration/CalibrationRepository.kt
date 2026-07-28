package com.poziomnica.calibration

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.poziomnica.domain.CalibrationProfile
import com.poziomnica.domain.LevelReading
import com.poziomnica.domain.MeasurementMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.calibrationStore by preferencesDataStore("calibration")

class CalibrationRepository(private val context: Context) {
    private val profilesKey = stringPreferencesKey("profiles")

    val profiles: Flow<List<CalibrationProfile>> = context.calibrationStore.data.map { p ->
        decode(p[profilesKey]).ifEmpty {
            listOf(CalibrationProfile("factory", "Fabryczna", isDefault = true, isActive = true))
        }
    }

    suspend fun quickZero(name: String, reading: LevelReading) {
        upsert(CalibrationProfile(UUID.randomUUID().toString(), name, reading.pitch, reading.roll, isActive = true, lastCalibratedAt = System.currentTimeMillis()))
    }

    suspend fun twoSided(name: String, first: LevelReading, second: LevelReading): Pair<Float, Float> {
        val correction = MeasurementMath.twoSidedCalibrationError(first, second)
        upsert(CalibrationProfile(UUID.randomUUID().toString(), name, correction.first, correction.second, isActive = true, lastCalibratedAt = System.currentTimeMillis()))
        return correction
    }

    suspend fun fourPointPlane(name: String, readings: List<LevelReading>): Pair<Float, Float>? {
        if (readings.size != 4) return null
        val pitchOffset = readings.map { it.surfaceY }.average().toFloat()
        val rollOffset = readings.map { it.surfaceX }.average().toFloat()
        upsertMerged(name) { active ->
            active.copy(
                offsetPitch = pitchOffset,
                offsetRoll = rollOffset
            )
        }
        return pitchOffset to rollOffset
    }

    suspend fun twoPointEdge(name: String, readings: List<LevelReading>): Pair<String, Float>? {
        if (readings.size != 2) return null
        val offset = readings.map { it.linearAngle }.average().toFloat()
        val firstEdge = readings.first().supportEdge
        val isShortEdge = readings.any { it.supportEdge.contains("krótka", ignoreCase = true) }
        val edgeLabel = if (isShortEdge) "krótka krawędź górna/dolna" else "długa krawędź lewa/prawa"
        val profileName = "$name - $edgeLabel"
        if (isShortEdge) {
            upsertMerged(profileName) { it.copy(shortEdgeOffset = offset) }
        } else {
            upsertMerged(profileName) { it.copy(longEdgeOffset = offset) }
        }
        return firstEdge to offset
    }

    suspend fun activate(id: String) = mutate { list -> list.map { it.copy(isActive = it.id == id) } }
    suspend fun createProfile(name: String) = mutate { list ->
        val active = list.firstOrNull { it.isActive } ?: CalibrationProfile("factory", "Fabryczna", isDefault = true)
        list.map { it.copy(isActive = false) } + active.copy(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Profil własny" },
            isDefault = false,
            isActive = true,
            lastCalibratedAt = System.currentTimeMillis()
        )
    }
    suspend fun delete(id: String) = mutate { list ->
        val filtered = list.filterNot { it.id == id || (it.id == "factory" && id == "factory") }
        val hasActive = filtered.any { it.isActive }
        val fallback = filtered.ifEmpty { listOf(CalibrationProfile("factory", "Fabryczna", isDefault = true, isActive = true)) }
        if (hasActive) fallback else fallback.mapIndexed { index, profile -> profile.copy(isActive = index == 0) }
    }
    suspend fun rename(id: String, name: String) = mutate { list -> list.map { if (it.id == id && !it.isDefault) it.copy(name = name.ifBlank { it.name }) else it } }
    suspend fun setDefault(id: String) = mutate { list -> list.map { it.copy(isDefault = it.id == id, isActive = it.id == id) } }
    suspend fun restoreFactory() = context.calibrationStore.edit { it.clear() }

    private suspend fun upsert(profile: CalibrationProfile) = mutate { list ->
        list.map { it.copy(isActive = false) } + profile
    }

    private suspend fun upsertMerged(name: String, transform: (CalibrationProfile) -> CalibrationProfile) {
        mutate { list ->
            val active = list.firstOrNull { it.isActive } ?: CalibrationProfile("factory", "Fabryczna", isDefault = true)
            val activeName = active.name.takeUnless { active.id == "factory" || it.isBlank() } ?: name
            val merged = transform(active).copy(
                id = active.id.takeUnless { it == "factory" } ?: UUID.randomUUID().toString(),
                name = activeName,
                isDefault = false,
                isActive = true,
                lastCalibratedAt = System.currentTimeMillis()
            )
            if (active.id == "factory") {
                list.map { it.copy(isActive = false) } + merged
            } else {
                list.map { if (it.id == active.id) merged else it.copy(isActive = false) }
            }
        }
    }

    private suspend fun mutate(transform: (List<CalibrationProfile>) -> List<CalibrationProfile>) {
        context.calibrationStore.edit { p ->
            val current = decode(p[profilesKey]).ifEmpty { listOf(CalibrationProfile("factory", "Fabryczna", isDefault = true)) }
            p[profilesKey] = encode(transform(current))
        }
    }

    private fun encode(list: List<CalibrationProfile>): String =
        list.joinToString("\n") {
            listOf(
                it.id,
                it.name,
                it.offsetPitch,
                it.offsetRoll,
                it.longEdgeOffset,
                it.shortEdgeOffset,
                it.isDefault,
                it.isActive,
                it.lastCalibratedAt
            ).joinToString("|")
        }

    private fun decode(raw: String?): List<CalibrationProfile> = raw.orEmpty().lines().mapNotNull { line ->
        val p = line.split("|")
        when (p.size) {
            7 -> CalibrationProfile(
                id = p[0],
                name = p[1],
                offsetPitch = p[2].toFloatOrNull() ?: 0f,
                offsetRoll = p[3].toFloatOrNull() ?: 0f,
                isDefault = p[4].toBoolean(),
                isActive = p[5].toBoolean(),
                lastCalibratedAt = p[6].toLongOrNull() ?: 0L
            )
            9 -> CalibrationProfile(
                id = p[0],
                name = p[1],
                offsetPitch = p[2].toFloatOrNull() ?: 0f,
                offsetRoll = p[3].toFloatOrNull() ?: 0f,
                longEdgeOffset = p[4].toFloatOrNull() ?: 0f,
                shortEdgeOffset = p[5].toFloatOrNull() ?: 0f,
                isDefault = p[6].toBoolean(),
                isActive = p[7].toBoolean(),
                lastCalibratedAt = p[8].toLongOrNull() ?: 0L
            )
            else -> null
        }
    }
}
