package com.poziomnica.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
    private var continuousTrack: AudioTrack? = null
    private var continuousToneHz: Int? = null
    private var continuousVolume: Float? = null
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
        wasInTolerance = false
    }

    fun test(settings: UserSettings) {
        playTone(settings, durationMs = 650)
    }

    private fun beep(settings: UserSettings) {
        playTone(settings, durationMs = 120)
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
        val volume = settings.volume.coerceIn(0f, 1f)
        if (continuousTrack != null && continuousToneHz == settings.toneHz && continuousVolume == volume) return
        stopContinuous()
        continuousTrack = buildToneTrack(settings, durationMs = 500).also { track ->
            track.setLoopPoints(0, track.bufferSizeInFrames, -1)
            track.play()
        }
        continuousToneHz = settings.toneHz
        continuousVolume = volume
    }

    private fun stopContinuous() {
        continuousTrack?.stop()
        continuousTrack?.release()
        continuousTrack = null
        continuousToneHz = null
        continuousVolume = null
    }

    private fun playTone(settings: UserSettings, durationMs: Int) {
        if (!settings.soundEnabled) return
        val track = buildToneTrack(settings, durationMs)
        track.setNotificationMarkerPosition(track.bufferSizeInFrames)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(audioTrack: AudioTrack) {
                audioTrack.release()
            }

            override fun onPeriodicNotification(audioTrack: AudioTrack) = Unit
        })
        track.play()
    }

    private fun buildToneTrack(settings: UserSettings, durationMs: Int): AudioTrack {
        val sampleRate = 44100
        val samples = (sampleRate * durationMs / 1000f).toInt().coerceAtLeast(1)
        val data = ShortArray(samples)
        val amplitude = Short.MAX_VALUE * settings.volume.coerceIn(0f, 1f) * 0.45f
        for (i in data.indices) {
            val angle = 2.0 * PI * i * settings.toneHz.coerceIn(220, 2200) / sampleRate
            data[i] = (sin(angle) * amplitude).toInt().toShort()
        }
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(data, 0, data.size) }
    }
}
