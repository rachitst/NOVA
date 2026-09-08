package com.nova.assistant.android.bluetooth

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nova.assistant.core.logging.NovaLogger
import com.nova.assistant.voice.service.NovaVoiceService

/**
 * BroadcastReceiver listening for Bluetooth connection/disconnection events.
 * Activates NovaVoiceService when a trusted earphone/headset connects if auto-start is enabled.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        NovaLogger.d("BluetoothReceiver", "Received broadcast action: $action")

        // Sync NovaVoiceService earphone state
        NovaVoiceService.syncState(context)
    }
}
