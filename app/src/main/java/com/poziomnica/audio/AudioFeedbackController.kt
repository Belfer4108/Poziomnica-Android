package com.poziomnica.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import com.poziomnica.domain.SoundMode
import com.poziomnica.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AudioFeedbackController {
    private var toneGenerator: ToneGenerator? = null
    private var continuousTrack: AudioTrack? = null
    private var proximityJob: Job? = null
    private var wasInTolerance = false

    fun update(inTolerance: Boolean, distance: Float, settings: UserSettings, scope: CoroutineScope) {
        if (!settings.soundEnabled) {
            stop()
            return
        }
        when (settings.soundMode) {
            SoundMode.SINGLE -> {
                if (inTolerance && !wasInTolerance) beep(settings)
                if (!inTolerance) wasInTolerance = false else wasInTolerance = true
            }
            SoundMode.CONTINUOUS -> if (inTolerance) startTone(settings) else stopContinuous()
            SoundMode.PROXIMITY -> startProximity(distance, inTolerance, settings, scope)
        }
    }

    fun stop() {
        proximityJob?.cancel()
        proximityJob = null
        stopContinuous()
        toneGenerator?.release()
        toneGenerator = null
        wasInTolerance = false
    }

    fun test(settings: UserSettings) {
        beep(settings)
    }

    private fun beep(settings: UserSettings) {
        val volume = (settings.volume * 100).toInt().coerceIn(0, 100)
        val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, volume).also { toneGenerator = it }
        generator.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
    }

    private fun startProximity(distance: Float, inTolerance: Boolean, settings: UserSettings, scope: CoroutineScope) {
        if (proximityJob?.isActive == true) return
        proximityJob = scope.launch {
            while (true) {
                if (inTolerance) {
                    startTone(settings)
                    delay(250)
                } else {
                    stopContinuous()
                    beep(settings)
                    val pause = (900 - (1f / (abs(distance) + 0.2f) * 130f)).toLong().coerceIn(120, 900)
                    delay(pause)
                }
            }
        }
    }

    private fun startTone(settings: UserSettings) {
        if (continuousTrack != null) return
        val sampleRate = 8000
        val samples = sampleRate / 2
        val data = ShortArray(samples)
        for (i in data.indices) {
            val angle = 2.0 * PI * i * settings.toneHz / sampleRate
            data[i] = (sin(angle) * Short.MAX_VALUE * settings.volume * 0.25f).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, data.size)
        track.setLoopPoints(0, data.size, -1)
        track.play()
        continuousTrack = track
    }

    private fun stopContinuous() {
        continuousTrack?.stop()
        continuousTrack?.release()
        continuousTrack = null
    }
}
