package com.nova.assistant.voice

import com.nova.assistant.voice.wakeword.NeuralWakeWordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

/**
 * Unit tests for NeuralWakeWordEngine.
 * Verifies silence rejection, continuous tone rejection, and frame extraction.
 */
class NeuralWakeWordEngineTest {

    private lateinit var engine: NeuralWakeWordEngine

    @Before
    fun setUp() {
        engine = NeuralWakeWordEngine(wakeWordPhrase = "Hey NOVA", sensitivity = 0.75f)
        engine.initialize()
    }

    @Test
    fun `phrase name is Hey NOVA`() {
        assertEquals("Hey NOVA", engine.wakeWordPhrase)
    }

    @Test
    fun `pure silence returns no detection`() {
        val silenceBuffer = ShortArray(3200) // 200ms of silence
        for (i in 0 until 5) {
            val result = engine.processPcmChunk(silenceBuffer, silenceBuffer.size)
            assertFalse("Silence must not trigger wake word", result.isDetected)
            assertEquals(0.0f, result.confidence, 0.001f)
        }
    }

    @Test
    fun `constant single frequency tone is rejected`() {
        // Continuous 1000Hz tone representing non-target single tone sound
        val toneBuffer = generateTone(durationMs = 800, freqHz = 1000f, amplitude = 5000)
        val result = engine.processPcmChunk(toneBuffer, toneBuffer.size)
        assertFalse("Single tone must not trigger 'Hey NOVA'", result.isDetected)
    }

    @Test
    fun `extractLogMelFrameStatic returns 40 mel coefficients`() {
        val tone = generateTone(durationMs = 50, freqHz = 440f, amplitude = 4000)
        val melFrame = NeuralWakeWordEngine.extractLogMelFrameStatic(tone, 0)
        assertTrue("Log-mel frame should be extracted", melFrame != null)
        assertEquals("Should extract exactly 40 mel bands", 40, melFrame!!.size)
    }

    @Test
    fun `reset clears internal spectrogram buffer`() {
        engine.reset()
        val silence = ShortArray(320)
        val result = engine.processPcmChunk(silence, silence.size)
        assertFalse(result.isDetected)
    }

    private fun generateTone(durationMs: Int, freqHz: Float, amplitude: Int): ShortArray {
        val sampleRate = 16000
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)
        val angleStep = (2.0 * Math.PI * freqHz) / sampleRate

        for (i in 0 until numSamples) {
            buffer[i] = (sin(i * angleStep) * amplitude).toInt().toShort()
        }
        return buffer
    }
}
