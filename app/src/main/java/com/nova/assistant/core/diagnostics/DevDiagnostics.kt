package com.nova.assistant.core.diagnostics

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.nova.assistant.core.config.NovaConfig
import com.nova.assistant.core.logging.NovaLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Individual timestamped pipeline diagnostic event.
 */
data class DiagnosticEvent(
    val timestampMs: Long = System.currentTimeMillis(),
    val stage: String,   // "PCM", "WAKE", "AUTH", "STT", "INTENT", "ACTION", "TTS"
    val status: String,  // "DETECTED", "CANDIDATE", "REJECTED", "AUTHORIZED", "HEARING", "SUCCESS", "ERROR"
    val details: String,
    val isSuccess: Boolean = true
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestampMs))
}

/**
 * Real-time telemetry state for DEV diagnostic overlay and live UI monitoring.
 */
data class DevTelemetryState(
    val isDevEnabled: Boolean = true,
    val activeAudioDevice: String = "Detecting...",
    val currentRms: Float = 0.0f,
    val currentPeak: Int = 0,
    val lastWakeScore: Float = 0.0f,
    val lastWakeThreshold: Float = 0.50f,
    val lastWakeDecision: String = "Standby (Awaiting 'Hey NOVA')",
    val isWakeCalibrated: Boolean = false,
    val lastSpeakerScore: Float = 0.0f,
    val lastSpeakerThreshold: Float = 0.48f,
    val lastSpeakerDecision: String = "Standby",
    val lastSpeakerName: String? = null,
    val lastSttTranscript: String = "",
    val recentEvents: List<DiagnosticEvent> = emptyList()
)

/**
 * Diagnostic messaging utility for development and physical hardware testing.
 * Automatically posts Toast notifications on the main thread when [NovaConfig.DEV] is true.
 * Provides real-time StateFlow telemetry for the on-screen DEV debug overlay.
 */
object DevDiagnostics {
    private var lastToastText: String? = null
    private var lastToastTime: Long = 0L

    private val _telemetry = MutableStateFlow(DevTelemetryState(isDevEnabled = NovaConfig.DEV))
    val telemetry: StateFlow<DevTelemetryState> = _telemetry.asStateFlow()

    fun show(context: Context?, message: String, isLong: Boolean = false) {
        // Structured Logcat message (always recorded for offline analysis)
        NovaLogger.i("Diagnostics", "[DEV] $message")

        if (!NovaConfig.DEV || context == null) return

        val now = System.currentTimeMillis()
        // Deduplicate identical messages sent within 800ms to avoid UI flooding
        if (message == lastToastText && now - lastToastTime < 800L) return
        lastToastText = message
        lastToastTime = now

        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    val duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    Toast.makeText(context.applicationContext, "⚡ [NOVA DEV]\n$message", duration).show()
                } catch (e: Exception) {
                    NovaLogger.w("Diagnostics", "Failed to display diagnostic Toast: ${e.message}")
                }
            }
        } catch (e: Exception) {
            NovaLogger.w("Diagnostics", "Error posting Toast to main thread", e)
        }
    }

    fun updateAudioStats(device: String, rms: Float, peak: Int) {
        if (!NovaConfig.DEV) return
        _telemetry.update {
            it.copy(
                activeAudioDevice = device,
                currentRms = rms,
                currentPeak = peak
            )
        }
    }

    fun updateWakeStats(score: Float, threshold: Float, decision: String, isCalibrated: Boolean) {
        if (!NovaConfig.DEV) return
        _telemetry.update {
            it.copy(
                lastWakeScore = score,
                lastWakeThreshold = threshold,
                lastWakeDecision = decision,
                isWakeCalibrated = isCalibrated
            )
        }
    }

    fun updateSpeakerStats(score: Float, threshold: Float, decision: String, speakerName: String?) {
        if (!NovaConfig.DEV) return
        _telemetry.update {
            it.copy(
                lastSpeakerScore = score,
                lastSpeakerThreshold = threshold,
                lastSpeakerDecision = decision,
                lastSpeakerName = speakerName
            )
        }
    }

    fun updateSttStats(transcript: String) {
        if (!NovaConfig.DEV) return
        _telemetry.update {
            it.copy(lastSttTranscript = transcript)
        }
    }

    fun logEvent(stage: String, status: String, details: String, isSuccess: Boolean = true) {
        val event = DiagnosticEvent(
            stage = stage,
            status = status,
            details = details,
            isSuccess = isSuccess
        )
        NovaLogger.i("Diagnostics", "[EVENT:${stage}][${status}] $details")

        if (!NovaConfig.DEV) return
        _telemetry.update { current ->
            val updated = listOf(event) + current.recentEvents.take(19)
            current.copy(recentEvents = updated)
        }
    }
}
