package com.nova.assistant.voice.wakeword

/**
 * Result returned by the WakeWordEngine for each processed audio chunk.
 */
data class WakeWordResult(
    val isDetected: Boolean,
    val confidence: Float = 0.0f,
    val keyword: String? = null
) {
    companion object {
        val NONE = WakeWordResult(isDetected = false)
        fun detected(confidence: Float, keyword: String = "Hey NOVA") =
            WakeWordResult(isDetected = true, confidence = confidence, keyword = keyword)
    }
}

/**
 * Interface defining local on-device wake-word detection.
 */
interface WakeWordEngine {
    val wakeWordPhrase: String

    /**
     * Initializes the wake-word engine.
     */
    fun initialize(): Boolean

    /**
     * Processes a chunk of 16kHz 16-bit mono PCM samples.
     * @param pcmData Buffer containing audio samples.
     * @param length Number of valid samples in the buffer.
     * @return [WakeWordResult] indicating whether the activation phrase was detected.
     */
    fun processPcmChunk(pcmData: ShortArray, length: Int): WakeWordResult

    /**
     * Resets internal temporal buffer and state tracking.
     */
    fun reset()

    /**
     * Releases any held resources.
     */
    fun release()
}
