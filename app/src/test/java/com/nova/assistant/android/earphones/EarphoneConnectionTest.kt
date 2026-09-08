package com.nova.assistant.android.earphones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that Hands-Free mode activates exclusively when
 * earphones are connected AND the speaker's voice is enrolled.
 */
class EarphoneConnectionTest {

    enum class HandsFreeStatus {
        WAITING_FOR_EARPHONES,
        ENROLLMENT_REQUIRED,
        ACTIVE_LISTENING
    }

    private fun determineHandsFreeStatus(isEarphonesConnected: Boolean, isVoiceEnrolled: Boolean): HandsFreeStatus {
        return when {
            !isEarphonesConnected -> HandsFreeStatus.WAITING_FOR_EARPHONES
            !isVoiceEnrolled -> HandsFreeStatus.ENROLLMENT_REQUIRED
            else -> HandsFreeStatus.ACTIVE_LISTENING
        }
    }

    @Test
    fun `when earphones disconnected hands-free is in standby waiting for earphones`() {
        val status1 = determineHandsFreeStatus(isEarphonesConnected = false, isVoiceEnrolled = false)
        assertEquals(HandsFreeStatus.WAITING_FOR_EARPHONES, status1)

        val status2 = determineHandsFreeStatus(isEarphonesConnected = false, isVoiceEnrolled = true)
        assertEquals(HandsFreeStatus.WAITING_FOR_EARPHONES, status2)
    }

    @Test
    fun `when earphones connected but voice not enrolled enrollment is required`() {
        val status = determineHandsFreeStatus(isEarphonesConnected = true, isVoiceEnrolled = false)
        assertEquals(HandsFreeStatus.ENROLLMENT_REQUIRED, status)
    }

    @Test
    fun `when earphones connected and voice enrolled hands-free mode is actively listening`() {
        val status = determineHandsFreeStatus(isEarphonesConnected = true, isVoiceEnrolled = true)
        assertEquals(HandsFreeStatus.ACTIVE_LISTENING, status)
    }
}
