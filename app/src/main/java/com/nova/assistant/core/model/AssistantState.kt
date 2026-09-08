package com.nova.assistant.core.model

import com.nova.assistant.actions.model.NovaAction

/**
 * Represents the high-level state of the NOVA assistant lifecycle.
 */
sealed interface AssistantState {
    /** Assistant is awaiting user interaction. */
    data object Idle : AssistantState

    /** Microphone is active and listening for user speech. */
    data object Listening : AssistantState

    /** Speech captured; converting speech to action and validating. */
    data class Processing(val rawTranscript: String) : AssistantState

    /** Spoken feedback is being played to the user. */
    data class Speaking(
        val spokenResponse: String,
        val triggeredAction: NovaAction? = null
    ) : AssistantState

    /** An error occurred during speech recognition, parsing, or execution. */
    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : AssistantState
}
