package com.nova.assistant.android.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences for Bluetooth earphone automatic activation.
 */
class BluetoothSettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "nova_bluetooth_settings"
        private const val KEY_AUTO_START = "auto_start_on_bluetooth"
        private const val KEY_TRUSTED_ADDRESSES = "trusted_bt_addresses"
        private const val KEY_TRUSTED_NAMES = "trusted_bt_names"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isAutoStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    fun getTrustedAddresses(): Set<String> {
        return prefs.getStringSet(KEY_TRUSTED_ADDRESSES, emptySet()) ?: emptySet()
    }

    fun addTrustedDevice(address: String, name: String?) {
        val currentAddresses = getTrustedAddresses().toMutableSet().apply { add(address) }
        val currentNames = (prefs.getStringSet(KEY_TRUSTED_NAMES, emptySet()) ?: emptySet()).toMutableSet()
        if (name != null) currentNames.add(name)

        prefs.edit()
            .putStringSet(KEY_TRUSTED_ADDRESSES, currentAddresses)
            .putStringSet(KEY_TRUSTED_NAMES, currentNames)
            .apply()
    }

    fun removeTrustedDevice(address: String) {
        val currentAddresses = getTrustedAddresses().toMutableSet().apply { remove(address) }
        prefs.edit().putStringSet(KEY_TRUSTED_ADDRESSES, currentAddresses).apply()
    }

    fun isDeviceTrusted(device: BluetoothDevice): Boolean {
        val trustedAddresses = getTrustedAddresses()
        // If user has not specified specific devices, any audio device is accepted when auto-start is ON
        if (trustedAddresses.isEmpty()) return true
        return device.address in trustedAddresses
    }
}
