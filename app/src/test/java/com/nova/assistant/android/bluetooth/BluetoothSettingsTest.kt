package com.nova.assistant.android.bluetooth

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BluetoothSettingsTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var settingsManager: BluetoothSettingsManager

    class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(data)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val data: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null && value != null) temp[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null && values != null) temp[key] = values.toSet()
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                temp.clear()
                return this
            }

            override fun commit(): Boolean {
                data.putAll(temp)
                return true
            }

            override fun apply() {
                data.putAll(temp)
            }
        }
    }

    private class FakeContext(private val prefs: SharedPreferences) : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        settingsManager = BluetoothSettingsManager(FakeContext(fakePrefs))
    }

    @Test
    fun autoStart_defaultsToFalse() {
        assertFalse(settingsManager.isAutoStartEnabled)
    }

    @Test
    fun autoStart_canBeToggled() {
        settingsManager.isAutoStartEnabled = true
        assertTrue(settingsManager.isAutoStartEnabled)

        settingsManager.isAutoStartEnabled = false
        assertFalse(settingsManager.isAutoStartEnabled)
    }

    @Test
    fun trustedDevices_addAndRemove() {
        assertTrue(settingsManager.getTrustedAddresses().isEmpty())

        settingsManager.addTrustedDevice("00:11:22:33:44:55", "Galaxy Buds Pro")
        assertEquals(setOf("00:11:22:33:44:55"), settingsManager.getTrustedAddresses())

        settingsManager.removeTrustedDevice("00:11:22:33:44:55")
        assertTrue(settingsManager.getTrustedAddresses().isEmpty())
    }
}
