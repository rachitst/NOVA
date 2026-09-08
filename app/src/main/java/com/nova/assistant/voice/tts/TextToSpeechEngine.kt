package com.nova.assistant.voice.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * State representation for the TextToSpeech engine.
 */
sealed interface TtsState {
    /** Engine is initializing or not yet ready. */
    data object Uninitialized : TtsState

    /** Engine is ready to speak. */
    data object Ready : TtsState

    /** Engine is currently synthesizing/speaking an utterance. */
    data class Speaking(val utteranceText: String) : TtsState

    /** Engine initialization or synthesis failed. */
    data class Error(val message: String) : TtsState
}

/**
 * Abstraction for Text-to-Speech generation.
 * Allows switching between Android TextToSpeech, Piper/Sherpa, or mock implementations.
 */
interface TextToSpeechEngine {
    /**
     * Observable state flow of the current TTS engine state.
     */
    val ttsState: StateFlow<TtsState>

    /**
     * Synthesizes and speaks the given text.
     * @param text The text to speak.
     * @param onDone Optional callback invoked when speech completes or fails.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null)

    /**
     * Stops any currently playing speech utterance immediately.
     */
    fun stop()

    /**
     * Releases TTS resources.
     */
    fun shutdown()
}
