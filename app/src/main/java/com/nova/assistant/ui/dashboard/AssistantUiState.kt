package com.nova.assistant.ui.dashboard

import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.NovaAction
import com.nova.assistant.core.diagnostics.DevTelemetryState
import com.nova.assistant.core.model.AssistantState
import com.nova.assistant.voice.speaker.EnrollmentState

/**
 * Historical record of an executed command and its result.
 */
data class ActionHistoryItem(
    val timestampMs: Long = System.currentTimeMillis(),
    val transcript: String,
    val action: NovaAction,
    val result: ActionResult
)

/**
 * Complete immutable UI state for the NOVA Assistant Dashboard.
 */
data class AssistantUiState(
    val assistantState: AssistantState = AssistantState.Idle,
    val liveTranscript: String = "",
    val lastTranscript: String? = null,
    val lastAction: NovaAction? = null,
    val lastResult: ActionResult? = null,
    val hasMicrophonePermission: Boolean = false,
    val isPermissionPermanentlyDenied: Boolean = false,
    val isTtsReady: Boolean = false,
    val isBackgroundServiceRunning: Boolean = false,
    val serviceStatusMessage: String = "Stopped",
    val isEarphonesConnected: Boolean = false,
    val isSpeakerEnrolled: Boolean = false,
    val enrolledSpeakerName: String? = null,
    val isEnrollmentDialogOpen: Boolean = false,
    val enrollmentState: EnrollmentState = EnrollmentState.Idle,
    val devTelemetry: DevTelemetryState = DevTelemetryState(),
    val actionHistory: List<ActionHistoryItem> = emptyList()
)
