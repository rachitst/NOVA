package com.nova.assistant.voice

import android.content.Context
import android.content.SharedPreferences
import com.nova.assistant.voice.speaker.LocalSpeakerEmbeddingVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

/**
 * Unit tests verifying mandatory speaker enrollment, voiceprint extraction,
 * authorization of enrolled speaker, and rejection of unknown speakers.
 */
class LocalSpeakerVerifierTest {

    private lateinit var fakeContext: FakeAppContext
    private lateinit var verifier: LocalSpeakerEmbeddingVerifier

    @Before
    fun setUp() {
        fakeContext = FakeAppContext()
        verifier = LocalSpeakerEmbeddingVerifier(fakeContext, verificationThreshold = 0.70f)
    }

    @Test
    fun `when not enrolled verify returns notEnrolled failure`() {
        assertFalse(verifier.isEnrolled)

        val dummyPcm = ShortArray(3200) { 1000 }
        val result = verifier.verify(dummyPcm, dummyPcm.size)

        assertFalse("Must NOT allow command access when not enrolled", result.isAuthorized)
        assertFalse(result.isEnrolled)
        assertTrue(result.feedbackMessage.contains("enrollment required"))
    }

    @Test
    fun `enrollment with valid voice samples succeeds and enables authorized authentication`() {
        // Generate synthetic voice-like periodic 150Hz PCM audio
        val sample1 = generateTonePcm(150.0, 16000, 4800)
        val sample2 = generateTonePcm(150.0, 16000, 4800)

        val enrolled = verifier.enroll("Alex", listOf(sample1, sample2))
        assertTrue("Enrollment should succeed", enrolled)
        assertTrue(verifier.isEnrolled)
        assertEquals("Alex", verifier.enrolledSpeakerName)

        // Test matching voice
        val matchingVoice = generateTonePcm(150.0, 16000, 4800)
        val authResult = verifier.verify(matchingVoice, matchingVoice.size)
        assertTrue("Enrolled speaker should be authorized", authResult.isAuthorized)
    }

    @Test
    fun `mismatching voice profile is rejected`() {
        // Enroll speaker at 120Hz (low pitch voice)
        val sample = generateTonePcm(120.0, 16000, 4800)
        verifier.enroll("UserLowPitch", listOf(sample))

        // Different speaker with 350Hz pitch and different acoustics
        val differentVoice = generateTonePcm(350.0, 16000, 4800)
        val result = verifier.verify(differentVoice, differentVoice.size)
        assertFalse("Different voice must be rejected", result.isAuthorized)
    }

    @Test
    fun `clear enrollment removes voice profile`() {
        val sample = generateTonePcm(200.0, 16000, 3200)
        verifier.enroll("User1", listOf(sample))
        assertTrue(verifier.isEnrolled)

        verifier.clearEnrollment()
        assertFalse(verifier.isEnrolled)
    }

    private fun generateTonePcm(freqHz: Double, sampleRate: Int, numSamples: Int): ShortArray {
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * freqHz * i / sampleRate
            pcm[i] = (sin(angle) * 12000.0).toInt().toShort()
        }
        return pcm
    }

    private class FakeAppContext : android.content.ContextWrapper(null) {
        private val fakePrefs = InMemorySharedPreferences()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = fakePrefs
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class EditorImpl : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { if (key != null && value != null) temp[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) temp[key] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) temp[key] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) temp[key] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) temp[key] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) map.remove(key) }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                map.putAll(temp)
            }
        }
    }
}
