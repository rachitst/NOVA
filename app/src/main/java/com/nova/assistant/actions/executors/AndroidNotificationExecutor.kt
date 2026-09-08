package com.nova.assistant.actions.executors

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.nova.assistant.actions.model.ActionErrorType
import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.android.notification.NovaNotificationListenerService
import com.nova.assistant.core.logging.NovaLogger

/**
 * Android implementation of NotificationExecutor.
 * Uses NovaNotificationListenerService to read actual pending notifications.
 */
class AndroidNotificationExecutor(
    private val context: Context
) : NotificationExecutor {

    override fun readNotifications(appFilter: String?): ActionResult {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        val hasAccess = enabledListeners.contains(context.packageName)

        if (!hasAccess) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return ActionResult.Failure(
                    spokenResponse = "Please enable notification access for NOVA in settings.",
                    displayFeedback = "Notification access required.",
                    errorType = ActionErrorType.PERMISSION_REQUIRED
                )
            } catch (e: Exception) {
                NovaLogger.e("NotificationExecutor", "Failed to launch notification settings", e)
                return ActionResult.Failure(
                    spokenResponse = "Notification access is required to read notifications.",
                    displayFeedback = "Notification permission needed.",
                    errorType = ActionErrorType.PERMISSION_REQUIRED
                )
            }
        }

        val allNotifications = NovaNotificationListenerService.activeNotifications
        val filtered = if (!appFilter.isNullOrBlank()) {
            val filterClean = appFilter.trim().lowercase()
            allNotifications.filter {
                it.appName.lowercase().contains(filterClean) ||
                it.packageName.lowercase().contains(filterClean)
            }
        } else {
            allNotifications
        }

        if (filtered.isEmpty()) {
            val filterSuffix = if (!appFilter.isNullOrBlank()) " from $appFilter" else ""
            return ActionResult.Success(
                spokenResponse = "You have no new notifications$filterSuffix.",
                displayFeedback = "No notifications$filterSuffix"
            )
        }

        // Summarize up to top 3 notifications
        val topNotifications = filtered.take(3)
        val summaries = topNotifications.map { n ->
            if (n.title.isNotBlank() && n.text.isNotBlank()) {
                "${n.appName} from ${n.title}: ${n.text}"
            } else if (n.title.isNotBlank()) {
                "${n.appName}: ${n.title}"
            } else {
                "${n.appName}: ${n.text}"
            }
        }

        val spokenResponse = if (summaries.size == 1) {
            "You have one notification: ${summaries[0]}"
        } else {
            "You have ${filtered.size} notifications. ${summaries.joinToString(". ")}."
        }

        return ActionResult.Success(
            spokenResponse = spokenResponse,
            displayFeedback = "${filtered.size} notifications: ${topNotifications.first().appName}"
        )
    }
}
