package com.nova.assistant.android.earphones

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.nova.assistant.core.logging.NovaLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages detection of connected earphones (both Bluetooth and wired).
 * Hands-Free mode activates exclusively when earphones are connected and voice profile is enrolled.
 */
class EarphoneConnectionManager(
    private val context: Context
) {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isEarphonesConnected = MutableStateFlow(checkConnected())
    val isEarphonesConnected: StateFlow<Boolean> = _isEarphonesConnected.asStateFlow()

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var headsetReceiver: BroadcastReceiver? = null
    private var isRegistered = false

    init {
        updateConnectionStatus()
    }

    /**
     * Checks if any wired or Bluetooth earphones/headphones are currently connected.
     */
    fun checkConnected(): Boolean {
        val am = audioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (isEarphoneDeviceType(device.type)) {
                    return true
                }
            }
            return false
        } else {
            @Suppress("DEPRECATION")
            return am.isWiredHeadsetOn || am.isBluetoothA2dpOn || am.isBluetoothScoOn
        }
    }

    private fun isEarphoneDeviceType(type: Int): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
            else -> false
        }
    }

    /**
     * Registers listeners for dynamic earphone connect/disconnect events.
     */
    fun register(onConnectionChanged: ((Boolean) -> Unit)? = null) {
        if (isRegistered) return
        isRegistered = true

        // 1. AudioDeviceCallback for modern Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    updateConnectionStatus(onConnectionChanged)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    updateConnectionStatus(onConnectionChanged)
                }
            }
            audioDeviceCallback = callback
            audioManager?.registerAudioDeviceCallback(callback, null)
        }

        // 2. BroadcastReceiver for wired headset plug and Bluetooth connection events
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateConnectionStatus(onConnectionChanged)
            }
        }
        headsetReceiver = receiver

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
        }
        context.registerReceiver(receiver, filter)

        updateConnectionStatus(onConnectionChanged)
        NovaLogger.d("EarphoneManager", "Earphone connection listeners registered.")
    }

    /**
     * Unregisters dynamic listeners to avoid leaks.
     */
    fun unregister() {
        if (!isRegistered) return
        isRegistered = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallback = null
        }

        if (headsetReceiver != null) {
            try {
                context.unregisterReceiver(headsetReceiver)
            } catch (_: Exception) {}
            headsetReceiver = null
        }
        NovaLogger.d("EarphoneManager", "Earphone connection listeners unregistered.")
    }

    fun updateConnectionStatus(listener: ((Boolean) -> Unit)? = null) {
        val connected = checkConnected()
        val changed = _isEarphonesConnected.value != connected
        _isEarphonesConnected.value = connected
        if (changed) {
            NovaLogger.i("EarphoneManager", "Earphones connection changed: connected=$connected")
            listener?.invoke(connected)
        }
    }
}
