package com.nova.assistant.actions.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nova.assistant.actions.executors.AndroidAppLauncher
import com.nova.assistant.actions.executors.AndroidCallExecutor
import com.nova.assistant.actions.executors.AndroidClockExecutor
import com.nova.assistant.actions.executors.AndroidDeviceControlExecutor
import com.nova.assistant.actions.executors.AndroidMessageExecutor
import com.nova.assistant.actions.executors.AndroidNotificationExecutor
import com.nova.assistant.actions.executors.AppLauncher
import com.nova.assistant.actions.executors.CallExecutor
import com.nova.assistant.actions.executors.ClockExecutor
import com.nova.assistant.actions.executors.DeviceControlExecutor
import com.nova.assistant.actions.executors.MessageExecutor
import com.nova.assistant.actions.executors.NotificationExecutor
import com.nova.assistant.actions.model.ActionErrorType
import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.NovaAction
import com.nova.assistant.android.security.DeviceLockManager
import com.nova.assistant.core.logging.NovaLogger
import java.net.URLEncoder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Default ActionManager implementation that acts as the security boundary.
 * Validates action arguments, verifies lock-screen permissions, and delegates
 * strictly to approved Android executors.
 */
class DefaultActionManager(
    private val context: Context,
    private val appLauncher: AppLauncher = AndroidAppLauncher(context),
    private val callExecutor: CallExecutor = AndroidCallExecutor(context),
    private val messageExecutor: MessageExecutor = AndroidMessageExecutor(context),
    private val clockExecutor: ClockExecutor = AndroidClockExecutor(context),
    private val deviceControlExecutor: DeviceControlExecutor = AndroidDeviceControlExecutor(context),
    private val notificationExecutor: NotificationExecutor = AndroidNotificationExecutor(context),
    private val deviceLockManager: DeviceLockManager = DeviceLockManager(context)
) : ActionManager {

    override suspend fun executeAction(action: NovaAction): ActionResult {
        NovaLogger.d("ActionManager", "Validating action: ${action::class.simpleName}")

        // Enforce lock-screen security boundary
        if (deviceLockManager.isDeviceLocked && !deviceLockManager.isActionAllowedWhileLocked(action)) {
            val explanation = deviceLockManager.getLockedExplanation(action)
            NovaLogger.w("ActionManager", "Action blocked on locked device: ${action::class.simpleName}")
            return ActionResult.Failure(
                spokenResponse = explanation,
                displayFeedback = "Device locked. Unlock required.",
                errorType = ActionErrorType.PERMISSION_REQUIRED
            )
        }

        return when (action) {
            is NovaAction.GetTime -> executeGetTime()
            is NovaAction.OpenApp -> executeOpenApp(action)
            is NovaAction.WebSearch -> executeWebSearch(action)
            is NovaAction.CallContact -> executeCallContact(action)
            is NovaAction.SendMessage -> executeSendMessage(action)
            is NovaAction.SetAlarm -> executeSetAlarm(action)
            is NovaAction.SetTimer -> executeSetTimer(action)
            is NovaAction.ReadNotifications -> executeReadNotifications(action)
            is NovaAction.DeviceControl -> executeDeviceControl(action)
            is NovaAction.Help -> executeHelp()
            is NovaAction.Unknown -> executeUnknown(action)
        }
    }

    private fun executeGetTime(): ActionResult {
        return try {
            val now = LocalTime.now()
            val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            val timeString = now.format(formatter)
            val response = "It is $timeString."
            NovaLogger.i("ActionManager", "GetTime executed: $response")
            ActionResult.Success(
                spokenResponse = response,
                displayFeedback = response
            )
        } catch (e: Exception) {
            NovaLogger.e("ActionManager", "Error formatting time", e)
            ActionResult.Failure(
                spokenResponse = "Unable to get the current time.",
                displayFeedback = "Error retrieving system time: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun executeOpenApp(action: NovaAction.OpenApp): ActionResult {
        return appLauncher.launchApp(action.target)
    }

    private fun executeCallContact(action: NovaAction.CallContact): ActionResult {
        return callExecutor.callContact(action.recipient)
    }

    private fun executeSendMessage(action: NovaAction.SendMessage): ActionResult {
        return messageExecutor.sendMessage(action.recipient, action.messageText, action.platform)
    }

    private fun executeSetAlarm(action: NovaAction.SetAlarm): ActionResult {
        val hour = action.hour
        val minute = action.minute ?: 0
        if (hour == null) {
            return ActionResult.Failure(
                spokenResponse = "What time should I set the alarm for?",
                displayFeedback = "Could not parse alarm time: \"${action.rawTime}\"",
                errorType = ActionErrorType.INVALID_PARAMETER
            )
        }
        return clockExecutor.setAlarm(hour, minute, action.label, action.rawTime)
    }

    private fun executeSetTimer(action: NovaAction.SetTimer): ActionResult {
        return clockExecutor.setTimer(action.durationSeconds, action.label)
    }

    private fun executeReadNotifications(action: NovaAction.ReadNotifications): ActionResult {
        return notificationExecutor.readNotifications(action.appFilter)
    }

    private fun executeDeviceControl(action: NovaAction.DeviceControl): ActionResult {
        return deviceControlExecutor.setFeatureState(action.feature, action.state)
    }

    private fun executeHelp(): ActionResult {
        val response = "I can check the time, launch apps, call contacts, send messages, set alarms, and control your flashlight."
        return ActionResult.Success(
            spokenResponse = response,
            displayFeedback = "NOVA Capabilities: Time, Apps, Calls, Messages, Alarms, Timers, Flashlight"
        )
    }

    private fun executeWebSearch(action: NovaAction.WebSearch): ActionResult {
        val query = action.query.trim()
        if (query.isBlank()) {
            NovaLogger.w("ActionManager", "WebSearch rejected: empty search query")
            return ActionResult.Failure(
                spokenResponse = "What would you like to search for?",
                displayFeedback = "Search query was empty.",
                errorType = ActionErrorType.INVALID_PARAMETER
            )
        }

        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUri = Uri.parse("https://www.google.com/search?q=$encodedQuery")
            val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            NovaLogger.i("ActionManager", "WebSearch launched for query: $query")
            ActionResult.Success(
                spokenResponse = "Searching Google for $query.",
                displayFeedback = "Searching: \"$query\""
            )
        } catch (e: Exception) {
            NovaLogger.e("ActionManager", "Error executing web search", e)
            ActionResult.Failure(
                spokenResponse = "Could not open web search.",
                displayFeedback = "Failed to launch browser: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun executeUnknown(action: NovaAction.Unknown): ActionResult {
        val raw = action.rawText.trim()
        val sanitized = NovaLogger.sanitize(raw)
        NovaLogger.i("ActionManager", "Unknown command encountered: '$sanitized'")

        val spokenResponse = if (raw.isBlank()) {
            "I didn't catch that."
        } else {
            "I didn't understand."
        }

        return ActionResult.Failure(
            spokenResponse = spokenResponse,
            displayFeedback = "Unrecognized: \"$raw\"",
            errorType = ActionErrorType.UNSUPPORTED_ACTION
        )
    }
}
