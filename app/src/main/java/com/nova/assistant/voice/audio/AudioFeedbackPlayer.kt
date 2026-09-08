package com.nova.assistant.voice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.nova.assistant.core.logging.NovaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Low-latency acoustic chime and feedback tone generator using in-memory PCM synthesis.
 * Does not require external audio assets and operates immediately across all Android versions.
 */
class AudioFeedbackPlayer {

    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Plays a pleasant two-tone rising chime indicating wake-word detection.
     */
    fun playWakeChime() {
        scope.launch {
            playToneSequence(
                listOf(
                    Tone(freqHz = 587.33f, durationMs = 70), // D5
                    Tone(freqHz = 880.00f, durationMs = 120) // A5
                )
            )
        }
    }

    /**
     * Plays a subtle confirmation chime when an action completes.
     */
    fun playSuccessChime() {
        scope.launch {
            playToneSequence(
                listOf(
                    Tone(freqHz = 783.99f, durationMs = 60), // G5
                    Tone(freqHz = 1046.50f, durationMs = 100) // C6
                )
            )
        }
    }

    /**
     * Plays a low error tone indicating unrecognized command or failure.
     */
    fun playErrorChime() {
        scope.launch {
            playToneSequence(
                listOf(
                    Tone(freqHz = 349.23f, durationMs = 100), // F4
                    Tone(freqHz = 261.63f, durationMs = 150)  // C4
                )
            )
        }
    }

    private fun playToneSequence(tones: List<Tone>) {
        val sampleRate = 44100
        val totalSamples = tones.sumOf { (it.durationMs * sampleRate) / 1000 }
        val pcmBuffer = ShortArray(totalSamples)

        var sampleIndex = 0
        for (tone in tones) {
            val numSamples = (tone.durationMs * sampleRate) / 1000
            val angleStep = (2.0 * Math.PI * tone.freqHz) / sampleRate

            for (i in 0 until numSamples) {
                // Apply a smooth cosine window envelope to avoid audio clicks at start/end
                val envelope = (0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * i / numSamples))).toFloat()
                val sampleValue = (sin(i * angleStep) * 0.75f * envelope * Short.MAX_VALUE).toInt()
                if (sampleIndex < totalSamples) {
                    pcmBuffer[sampleIndex++] = sampleValue.toShort()
                }
            }
        }

        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcmBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
            audioTrack.play()
            Thread.sleep(tones.sumOf { it.durationMs.toLong() } + 50L)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            NovaLogger.w("AudioFeedbackPlayer", "Failed to play audio chime: ${e.message}")
        }
    }

    private data class Tone(val freqHz: Float, val durationMs: Int)
}
