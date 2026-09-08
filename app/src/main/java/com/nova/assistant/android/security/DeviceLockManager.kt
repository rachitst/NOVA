package com.nova.assistant.android.security

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.nova.assistant.actions.model.NovaAction
import com.nova.assistant.core.logging.NovaLogger

/**
 * Manages device lock state and enforces security boundaries for locked-screen interactions.
 * Safe actions (time, alarm, timer, flashlight) are allowed while locked.
 * Sensitive actions (calls, messaging, notifications, app launching) require device unlock.
 */
class DeviceLockManager(
    private val context: Context
) {

    private val keyguardManager: KeyguardManager? =
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /**
     * Checks if the device keyguard is currently locked.
     */
    val isDeviceLocked: Boolean
        get() = keyguardManager?.isKeyguardLocked == true || keyguardManager?.isDeviceLocked == true

    /**
     * Checks if the screen is currently interactive (turned on).
     */
    val isScreenOn: Boolean
        get() = powerManager?.isInteractive == true

    /**
     * Determines whether the given NovaAction is permitted to execute while the screen is locked.
     */
    fun isActionAllowedWhileLocked(action: NovaAction): Boolean {
        return when (action) {
            is NovaAction.GetTime -> true
            is NovaAction.SetAlarm -> true
            is NovaAction.SetTimer -> true
            is NovaAction.DeviceControl -> true
            is NovaAction.Help -> true
            is NovaAction.Unknown -> true
            is NovaAction.CallContact -> false
            is NovaAction.SendMessage -> false
            is NovaAction.ReadNotifications -> false
            is NovaAction.OpenApp -> false
            is NovaAction.WebSearch -> false
        }
    }

    /**
     * Provides a concise user-facing explanation when an action is blocked due to screen lock.
     */
    fun getLockedExplanation(action: NovaAction): String {
        return when (action) {
            is NovaAction.CallContact -> "Please unlock your device to call ${action.recipient}."
            is NovaAction.SendMessage -> "Please unlock your device to send messages."
            is NovaAction.ReadNotifications -> "Please unlock your device to read notifications."
            is NovaAction.OpenApp -> "Please unlock your device to open apps."
            is NovaAction.WebSearch -> "Please unlock your device to search the web."
            else -> "Please unlock your device first."
        }
    }
}
