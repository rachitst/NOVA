package com.nova.assistant.voice.speaker

/**
 * Result returned by the speaker verification system.
 * Clearly separates wake-word detection from speaker authentication.
 */
data class SpeakerVerificationResult(
    val isAuthorized: Boolean,
    val confidence: Float,
    val isEnrolled: Boolean,
    val feedbackMessage: String
) {
    companion object {
        fun disabled() =
            SpeakerVerificationResult(
                isAuthorized = true,
                confidence = 1.0f,
                isEnrolled = false,
                feedbackMessage = "Speaker verification is disabled."
            )

        fun notEnrolled() =
            SpeakerVerificationResult(
                isAuthorized = false,
                confidence = 0.0f,
                isEnrolled = false,
                feedbackMessage = "Voice enrollment required before speaker-verified activation."
            )

        fun authorized(confidence: Float, name: String) =
            SpeakerVerificationResult(
                isAuthorized = true,
                confidence = confidence,
                isEnrolled = true,
                feedbackMessage = "Authorized voice: $name"
            )

        fun unauthorized(confidence: Float) =
            SpeakerVerificationResult(
                isAuthorized = false,
                confidence = confidence,
                isEnrolled = true,
                feedbackMessage = "Voice does not match enrolled profile."
            )
    }
}

/**
 * Interface defining local on-device speaker verification and voiceprint matching.
 *
 * NOTE ON SECURITY & PRIVACY:
 * This layer performs acoustic voiceprint similarity filtering on-device.
 * It is NOT cryptographic or biometric device security (such as Android BiometricPrompt).
 * High-consequence actions must always require on-screen confirmation or device unlock.
 */
interface SpeakerVerifier {
    val isEnrolled: Boolean
    val enrolledSpeakerName: String?
    var isVerificationEnabled: Boolean

    /**
     * Enrolls the authorized user voiceprint from a list of audio sample buffers.
     * @param speakerName Name/label for the voice profile.
     * @param audioSamples Collection of 16kHz PCM audio buffers spoken by the user.
     * @return True if enrollment succeeds with valid acoustic embeddings.
     */
    fun enroll(speakerName: String, audioSamples: List<ShortArray>): Boolean

    /**
     * Verifies if the captured speech audio matches the enrolled authorized voiceprint.
     */
    fun verify(pcmChunk: ShortArray, length: Int): SpeakerVerificationResult

    /**
     * Clears all enrolled voiceprint data.
     */
    fun clearEnrollment()
}
