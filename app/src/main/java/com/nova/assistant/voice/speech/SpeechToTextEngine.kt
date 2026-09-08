package com.nova.assistant.voice.speech

import kotlinx.coroutines.flow.StateFlow

/**
 * Standard error categories for speech-to-text operations.
 */
enum class SpeechError {
    NO_SPEECH,
    TIMEOUT,
    AUDIO_ERROR,
    NETWORK_ERROR,
    PERMISSION_DENIED,
    RECOGNITION_UNAVAILABLE,
    CLIENT_ERROR,
    UNKNOWN
}

/**
 * Lifecycle and recognition states for the SpeechToText engine.
 */
sealed interface SpeechState {
    /** Engine is idle and not capturing audio. */
    data object Idle : SpeechState

    /** Engine is ready and actively capturing microphone audio. */
    data object Listening : SpeechState

    /** Partial/interim hypothesis from the recognizer. */
    data class PartialResult(val partialText: String) : SpeechState

    /** Final recognized transcript. */
    data class FinalResult(val transcript: String) : SpeechState

    /** Recognition error occurred. */
    data class Error(val error: SpeechError, val message: String) : SpeechState
}

/**
 * Abstraction for speech recognition engine.
 * Allows switching between Android SpeechRecognizer, whisper.cpp, or mock implementations.
 */
interface SpeechToTextEngine {
    /**
     * Observable state flow of the current speech recognition state.
     */
    val speechState: StateFlow<SpeechState>

    /**
     * Checks if speech recognition service is available on the device.
     */
    fun isRecognitionAvailable(): Boolean

    /**
     * Starts listening for user speech.
     */
    fun startListening()

    /**
     * Stops listening and requests final transcription of captured audio.
     */
    fun stopListening()

    /**
     * Cancels active listening and discards audio without processing.
     */
    fun cancelListening()

    /**
     * Releases system speech recognition resources.
     */
    fun destroy()
}
