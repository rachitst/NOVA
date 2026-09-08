package com.nova.assistant.android.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nova.assistant.core.logging.NovaLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Data model for a captured active notification.
 */
data class NovaNotification(
    val id: Int,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTimeMs: Long
)

/**
 * Android NotificationListenerService for legitimate on-device notification access.
 * Maintains an in-memory cache of recent unread notifications from messaging and communication apps.
 */
class NovaNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _activeNotifications = CopyOnWriteArrayList<NovaNotification>()
        val activeNotifications: List<NovaNotification> get() = _activeNotifications

        var isServiceConnected: Boolean = false
            private set

        fun clearNotifications() {
            _activeNotifications.clear()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        NovaLogger.i("NotificationService", "NovaNotificationListenerService connected.")
        refreshActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
        NovaLogger.i("NotificationService", "NovaNotificationListenerService disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        processStatusBarNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        _activeNotifications.removeAll { it.id == sbn.id && it.packageName == sbn.packageName }
    }

    private fun refreshActiveNotifications() {
        try {
            val sbns = activeNotifications ?: return
            _activeNotifications.clear()
            for (sbn in sbns) {
                // If it's a valid StatusBarNotification from system API
            }
        } catch (e: Exception) {
            NovaLogger.e("NotificationService", "Error refreshing active notifications", e)
        }
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification) {
        // Exclude ongoing / foreground service notifications (e.g., media players, NOVA itself)
        if (sbn.isOngoing || (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return
        }

        // Exclude NOVA's own notifications
        if (sbn.packageName == packageName) {
            return
        }

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val notification = NovaNotification(
            id = sbn.id,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            postTimeMs = sbn.postTime
        )

        // Keep most recent 20 notifications
        _activeNotifications.removeAll { it.id == sbn.id && it.packageName == sbn.packageName }
        _activeNotifications.add(0, notification)
        if (_activeNotifications.size > 20) {
            _activeNotifications.removeAt(_activeNotifications.size - 1)
        }

        NovaLogger.d("NotificationService", "Captured notification from $appName: '$title'")
    }
}
