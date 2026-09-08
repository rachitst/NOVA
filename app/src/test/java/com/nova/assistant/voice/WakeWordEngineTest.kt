package com.nova.assistant.voice

import com.nova.assistant.voice.wakeword.MfccWakeWordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

/**
 * Unit tests for DSP MFCC Keyword Spotter ("Hey NOVA").
 * Verifies noise rejection, silence filtering, DTW sequence alignment, and calibration.
 */
class WakeWordEngineTest {

    private lateinit var engine: MfccWakeWordEngine

    @Before
    fun setUp() {
        engine = MfccWakeWordEngine(wakeWordPhrase = "Hey NOVA", sensitivity = 0.75f)
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
            assertFalse(result.isDetected)
            assertEquals(0.0f, result.confidence, 0.001f)
        }
    }

    @Test
    fun `constant broadband noise is rejected by VAD and spectral gate`() {
        val noiseBuffer = ShortArray(3200)
        for (i in noiseBuffer.indices) {
            noiseBuffer[i] = if (i % 2 == 0) 2500.toShort() else (-2500).toShort()
        }

        for (chunk in 0 until 10) {
            val result = engine.processPcmChunk(noiseBuffer, noiseBuffer.size)
            assertFalse("Broadband noise must NEVER trigger wake word", result.isDetected)
        }
    }

    @Test
    fun `arbitrary single tone speech sound is rejected without complete phonetic sequence`() {
        // Continuous 1000Hz tone representing non-target single vowel speech
        val toneBuffer = generateTone(durationMs = 800, freqHz = 1000f, amplitude = 5000)
        val result = engine.processPcmChunk(toneBuffer, toneBuffer.size)
        assertFalse("Single tone must not trigger 'Hey NOVA'", result.isDetected)
    }

    @Test
    fun `findSpeechBoundaries isolates active speech region from leading and trailing silence`() {
        val totalSamples = 16000 * 2 // 2 seconds
        val buffer = ShortArray(totalSamples)

        // Add 600ms of active speech in the middle (sample 8000 to 17600)
        for (i in 8000 until 17600) {
            val angle = 2.0 * Math.PI * 250.0 * i / 16000
            buffer[i] = (sin(angle) * 8000.0).toInt().toShort()
        }

        val (start, end) = MfccWakeWordEngine.findSpeechBoundaries(buffer, buffer.size)
        assertTrue("Start boundary should be near active onset (got $start)", start in 6400..9600)
        assertTrue("End boundary should be near active offset (got $end)", end in 16000..20000)
        assertTrue("Active duration should be at least 6000 samples", (end - start) >= 6000)
    }

    @Test
    fun `computeDtwSimilarity returns high score for identical sequences and low for orthogonal`() {
        val template = MfccWakeWordEngine.DEFAULT_DTW_TEMPLATE
        val identicalList = template.toList()

        val matchScore = MfccWakeWordEngine.computeDtwSimilarity(identicalList, template)
        assertTrue("Identical dynamic sequence should have score > 0.85 (got $matchScore)", matchScore > 0.85f)

        // Inverted sequence
        val invertedList = template.map { vec ->
            FloatArray(vec.size) { i -> -vec[i] }
        }
        val invertedScore = MfccWakeWordEngine.computeDtwSimilarity(invertedList, template)
        assertTrue("Inverted sequence must have low score below candidate threshold (got $invertedScore)", invertedScore < 0.38f)
    }

    @Test
    fun `Hello template is rejected by competitor check`() {
        val helloTemplate = MfccWakeWordEngine.HELLO_TEMPLATE
        val helloList = helloTemplate.toList()

        // Overall similarity to Hey NOVA should be lower than to Hello
        val heyNovaScore = MfccWakeWordEngine.computeDtwSimilarity(helloList, MfccWakeWordEngine.DEFAULT_DTW_TEMPLATE, checkVariance = false)
        val helloScore = MfccWakeWordEngine.computeDtwSimilarity(helloList, MfccWakeWordEngine.HELLO_TEMPLATE, checkVariance = false)

        assertTrue("Hello score ($helloScore) should exceed Hey NOVA score ($heyNovaScore) on Hello speech", helloScore >= heyNovaScore)
    }

    @Test
    fun `reset clears internal frame buffer`() {
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
