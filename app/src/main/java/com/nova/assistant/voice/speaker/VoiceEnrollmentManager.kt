package com.nova.assistant.voice.speaker

import android.content.Context
import com.nova.assistant.core.diagnostics.DevDiagnostics
import com.nova.assistant.core.logging.NovaLogger
import com.nova.assistant.voice.audio.AudioStreamManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * State representing voice enrollment progress.
 */
sealed interface EnrollmentState {
    data object Idle : EnrollmentState
    data class ReadyForSample(val currentSampleIndex: Int, val totalSamples: Int, val speakerName: String) : EnrollmentState
    data class RecordingSample(val currentSampleIndex: Int, val totalSamples: Int, val audioRms: Float, val progressPercent: Float) : EnrollmentState
    data class Processing(val sampleIndex: Int) : EnrollmentState
    data class SampleRetry(val sampleIndex: Int, val totalSamples: Int, val reason: String) : EnrollmentState
    data class Success(val speakerName: String) : EnrollmentState
    data class Error(val message: String) : EnrollmentState
}

/**
 * Orchestrates secure on-device voice profile enrollment.
 * Guides the user through recording 3 distinct voice samples of "Hey NOVA" to generate
 * an acoustic voiceprint embedding without storing raw audio recordings.
 */
class VoiceEnrollmentManager(
    private val context: Context,
    private val speakerVerifier: SpeakerVerifier = LocalSpeakerEmbeddingVerifier(context)
) {

    companion object {
        const val REQUIRED_SAMPLES = 3
        private const val SAMPLES_COUNT = (16000 * 1.8).toInt() // 28,800 samples for 1.8s at 16kHz
    }

    private val audioStreamManager = AudioStreamManager(context)
    private val collectedSamples = mutableListOf<ShortArray>()

    private val _enrollmentState = MutableStateFlow<EnrollmentState>(EnrollmentState.Idle)
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState.asStateFlow()

    private var currentRecordingBuffer = ShortArray(SAMPLES_COUNT)
    private var recordedSampleOffset = 0
    private var targetSpeakerName = "Owner"
    private var activeSampleIndex = 1

    fun startEnrollmentSession(speakerName: String) {
        targetSpeakerName = speakerName.trim().ifBlank { "Owner" }
        collectedSamples.clear()
        recordedSampleOffset = 0
        activeSampleIndex = 1
        _enrollmentState.value = EnrollmentState.ReadyForSample(1, REQUIRED_SAMPLES, targetSpeakerName)
        NovaLogger.i("VoiceEnrollment", "Started voice enrollment session for: '$targetSpeakerName'")
        DevDiagnostics.show(context, "Voice Enrollment Started for '$targetSpeakerName'")
    }

    fun recordNextSample() {
        startSampleRecording(activeSampleIndex)
    }

    private fun startSampleRecording(sampleIndex: Int) {
        recordedSampleOffset = 0
        currentRecordingBuffer = ShortArray(SAMPLES_COUNT)
        _enrollmentState.value = EnrollmentState.RecordingSample(sampleIndex, REQUIRED_SAMPLES, 0.0f, 0.0f)
        DevDiagnostics.show(context, "Recording Sample $sampleIndex of $REQUIRED_SAMPLES... Speak 'Hey NOVA'")

        audioStreamManager.startStreaming { pcmData, length ->
            if (_enrollmentState.value !is EnrollmentState.RecordingSample) return@startStreaming

            val spaceLeft = SAMPLES_COUNT - recordedSampleOffset
            val toCopy = length.coerceAtMost(spaceLeft)
            System.arraycopy(pcmData, 0, currentRecordingBuffer, recordedSampleOffset, toCopy)
            recordedSampleOffset += toCopy

            var sum = 0.0
            for (i in 0 until length) {
                sum += (pcmData[i] * pcmData[i]).toDouble()
            }
            val rms = sqrt(sum / length).toFloat()
            val progressPercent = (recordedSampleOffset.toFloat() / SAMPLES_COUNT).coerceIn(0.0f, 1.0f)

            _enrollmentState.value = EnrollmentState.RecordingSample(sampleIndex, REQUIRED_SAMPLES, rms, progressPercent)

            if (recordedSampleOffset >= SAMPLES_COUNT) {
                audioStreamManager.pauseStreaming()
                onSampleCaptured(sampleIndex)
            }
        }
    }

    private fun onSampleCaptured(sampleIndex: Int) {
        _enrollmentState.value = EnrollmentState.Processing(sampleIndex)

        val validation = validateSpeechSample(currentRecordingBuffer)
        if (!validation.isValid) {
            NovaLogger.w("VoiceEnrollment", "Sample $sampleIndex failed speech validation: ${validation.reason}")
            DevDiagnostics.show(context, "Sample $sampleIndex Retry: ${validation.reason}", true)
            _enrollmentState.value = EnrollmentState.SampleRetry(
                sampleIndex = sampleIndex,
                totalSamples = REQUIRED_SAMPLES,
                reason = validation.reason
            )
            return
        }

        collectedSamples.add(currentRecordingBuffer.copyOf())
        NovaLogger.i("VoiceEnrollment", "Captured valid voice sample $sampleIndex of $REQUIRED_SAMPLES")
        DevDiagnostics.show(context, "Sample $sampleIndex Accepted ✓")

        if (sampleIndex < REQUIRED_SAMPLES) {
            activeSampleIndex = sampleIndex + 1
            _enrollmentState.value = EnrollmentState.ReadyForSample(activeSampleIndex, REQUIRED_SAMPLES, targetSpeakerName)
        } else {
            finalizeEnrollment()
        }
    }

    private fun validateSpeechSample(pcm: ShortArray): ValidationResult {
        if (pcm.size < 8000) {
            return ValidationResult(false, "Audio recording was too short.")
        }

        val frameLen = 320 // 20ms at 16kHz
        val numFrames = pcm.size / frameLen

        var maxAbsoluteAmp = 0
        val frameRmsList = FloatArray(numFrames)
        var voicedFramesCount = 0
        var totalEnergy = 0.0

        for (f in 0 until numFrames) {
            val offset = f * frameLen
            var frameSum = 0.0
            var localMax = 0

            for (i in 0 until frameLen) {
                val sample = pcm[offset + i].toInt()
                val absVal = kotlin.math.abs(sample)
                if (absVal > localMax) localMax = absVal
                frameSum += (sample.toDouble() * sample.toDouble())
            }

            if (localMax > maxAbsoluteAmp) maxAbsoluteAmp = localMax
            totalEnergy += frameSum

            val rms = sqrt(frameSum / frameLen).toFloat()
            frameRmsList[f] = rms

            // Periodic vocal pitch check for speech frame detection (80Hz to 400Hz)
            if (rms > 180f) {
                val minLag = 16000 / 400 // 40 samples
                val maxLag = 16000 / 80  // 200 samples
                var maxAutocorr = 0.0

                for (lag in minLag..maxLag.coerceAtMost(frameLen / 2)) {
                    var num = 0.0
                    var den1 = 0.0
                    var den2 = 0.0
                    for (i in 0 until frameLen - lag step 2) {
                        val s1 = pcm[offset + i].toDouble()
                        val s2 = pcm[offset + i + lag].toDouble()
                        num += s1 * s2
                        den1 += s1 * s1
                        den2 += s2 * s2
                    }
                    val denom = sqrt(den1 * den2)
                    if (denom > 1e-4) {
                        val r = num / denom
                        if (r > maxAutocorr) maxAutocorr = r
                    }
                }

                if (maxAutocorr > 0.18) {
                    voicedFramesCount++
                }
            }
        }

        // 1. Peak Amplitude Check: Must contain speech above whisper/silence
        if (maxAbsoluteAmp < 1000) {
            NovaLogger.w("VoiceEnrollment", "Sample rejected: max amplitude too low ($maxAbsoluteAmp < 1000). Silence/whisper.")
            return ValidationResult(false, "No speech detected. Please speak 'Hey NOVA' clearly into the microphone.")
        }

        // 2. Minimum Voiced Speech Duration (must contain at least 80ms of periodic vocal speech)
        if (voicedFramesCount < 4) {
            NovaLogger.w("VoiceEnrollment", "Sample rejected: insufficient voiced frames ($voicedFramesCount < 4).")
            return ValidationResult(false, "Voice unclear. Please speak 'Hey NOVA' clearly.")
        }

        NovaLogger.i("VoiceEnrollment", "Sample passed validation: maxAmp=$maxAbsoluteAmp, voicedFrames=$voicedFramesCount")
        return ValidationResult(true)
    }

    private data class ValidationResult(val isValid: Boolean, val reason: String = "")

    private fun finalizeEnrollment() {
        audioStreamManager.stopStreaming()
        DevDiagnostics.show(context, "Calibrating 3 Voice Samples...")
        val success = speakerVerifier.enroll(targetSpeakerName, collectedSamples)
        if (success && speakerVerifier.isEnrolled) {
            NovaLogger.i("VoiceEnrollment", "Enrollment completed successfully for '$targetSpeakerName'. Profile verified.")
            _enrollmentState.value = EnrollmentState.Success(targetSpeakerName)
            DevDiagnostics.show(context, "Voice Profile ENROLLED & VERIFIED for '$targetSpeakerName' ✓", true)
        } else {
            NovaLogger.w("VoiceEnrollment", "Enrollment failed during acoustic feature calibration.")
            _enrollmentState.value = EnrollmentState.Error("Acoustic calibration failed. Please try again.")
            DevDiagnostics.show(context, "Enrollment Failed: Feature calibration error", true)
        }
    }

    fun cancelEnrollment() {
        audioStreamManager.stopStreaming()
        collectedSamples.clear()
        _enrollmentState.value = EnrollmentState.Idle
        NovaLogger.i("VoiceEnrollment", "Enrollment session reset.")
    }
}
